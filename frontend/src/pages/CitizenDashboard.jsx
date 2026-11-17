import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api, { errMsg } from '../api/client';
import { useAuth } from '../context/AuthContext';
import Alert from '../components/Alert';
import StatusStepper from '../components/StatusStepper';
import { StatusBadge } from '../components/Badges';
import { label, fmtDate } from '../constants';

export default function CitizenDashboard() {
  const { user } = useAuth();
  const nav = useNavigate();
  const [stats, setStats] = useState(null);
  const [recent, setRecent] = useState([]);
  const [notes, setNotes] = useState([]);
  const [tid, setTid] = useState('');
  const [err, setErr] = useState('');

  useEffect(() => {
    Promise.all([api.get('/complaints/stats'), api.get('/complaints', { params: { size: 5, sort: 'updatedAt,desc' } }), api.get('/notifications', { params: { size: 4 } })])
      .then(([s, r, n]) => { setStats(s.data); setRecent(r.data.content); setNotes(n.data.content); })
      .catch((e) => setErr(errMsg(e)));
  }, []);

  const active = stats ? stats.SUBMITTED + stats.UNDER_REVIEW + stats.ASSIGNED + stats.IN_PROGRESS + stats.RESOLUTION_SUBMITTED + stats.REOPENED : 0;
  const cards = stats && [['📋', 'Total reported', stats.TOTAL], ['⏳', 'Being handled', active], ['✅', 'Resolved', stats.RESOLVED], ['🚫', 'Rejected', stats.REJECTED]];
  const latest = recent[0];

  return (
    <>
      <div className="hero">
        <div>
          <h1>Hello, {user.name.split(' ')[0]} 👋</h1>
          <p>See the city around you? Report it, and follow every step until it is fixed.</p>
          <div className="row">
            <Link className="btn btn-light" to="/complaints/new">➕ Report an issue</Link>
            <form className="inline-track" onSubmit={(e) => { e.preventDefault(); if (tid.trim()) nav(`/track?id=${encodeURIComponent(tid.trim())}`); }}>
              <input placeholder="Track by Complaint ID" value={tid} onChange={(e) => setTid(e.target.value)} />
              <button className="btn btn-light">Track</button>
            </form>
          </div>
        </div>
      </div>
      <Alert>{err}</Alert>
      {!stats ? <div className="loading">Loading…</div> : (
        <>
          <div className="grid grid-stats">
            {cards.map(([ico, lbl, n]) => <div key={lbl} className="card stat"><div className="ico">{ico}</div><div><div className="num">{n}</div><div className="lbl">{lbl}</div></div></div>)}
          </div>
          {latest && (
            <div className="card mt">
              <div className="row between"><h2>Latest activity: <Link to={`/complaints/${latest.id}`}>{latest.title}</Link></h2><span className="tracking-id">{latest.trackingId}</span></div>
              <StatusStepper status={latest.status} />
            </div>
          )}
          <div className="grid grid-2 mt">
            <div className="card">
              <div className="row between"><h2>My recent complaints</h2><Link to="/complaints">View all →</Link></div>
              {recent.length === 0 ? <div className="empty">You have not reported anything yet.</div> : (
                <div className="table-wrap"><table>
                  <thead><tr><th>ID</th><th>Title</th><th>Status</th><th>Updated</th></tr></thead>
                  <tbody>{recent.map((c) => (
                    <tr key={c.id}><td><Link to={`/complaints/${c.id}`}>{c.trackingId}</Link></td><td className="wrap">{c.title}</td><td><StatusBadge status={c.status} /></td><td>{fmtDate(c.updatedAt)}</td></tr>
                  ))}</tbody>
                </table></div>
              )}
            </div>
            <div className="card">
              <div className="row between"><h2>Notifications</h2><Link to="/notifications">View all →</Link></div>
              {notes.length === 0 ? <div className="empty">Nothing new.</div> : notes.map((n) => (
                <div key={n.id} className={`note-row ${n.read ? '' : 'unread'}`}>
                  <div>{n.complaintId ? <Link to={`/complaints/${n.complaintId}`}>{n.message}</Link> : n.message}</div>
                  <small className="muted">{label(n.type)} · {fmtDate(n.createdAt)}</small>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </>
  );
}
