import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errMsg } from '../api/client';
import { useAuth } from '../context/AuthContext';
import Alert from '../components/Alert';
import { StatusBadge, PriorityBadge, SlaBadge } from '../components/Badges';
import { label, fmtDate } from '../constants';

function TaskTable({ rows, empty }) {
  if (rows.length === 0) return <div className="empty">{empty}</div>;
  return (
    <div className="table-wrap"><table>
      <thead><tr><th>ID</th><th>Title</th><th>Priority</th><th>Status</th><th>SLA</th><th>Assigned/Updated</th><th /></tr></thead>
      <tbody>{rows.map((c) => (
        <tr key={c.id}>
          <td><Link to={`/complaints/${c.id}`}><strong>{c.trackingId}</strong></Link></td>
          <td className="wrap">{c.title}<div className="muted" style={{ fontSize: '.8rem' }}>{label(c.category)}</div></td>
          <td><PriorityBadge priority={c.priority} /></td>
          <td><StatusBadge status={c.status} />{c.status === 'ASSIGNED' && !c.acceptedAt && <div className="muted" style={{ fontSize: '.75rem' }}>Not accepted yet</div>}</td>
          <td><SlaBadge c={c} /></td>
          <td>{fmtDate(c.updatedAt)}</td>
          <td><Link className="btn btn-sm" to={`/complaints/${c.id}`}>Open</Link></td>
        </tr>
      ))}</tbody>
    </table></div>
  );
}

export default function OfficerDashboard() {
  const { user } = useAuth();
  const [rows, setRows] = useState(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.get('/complaints', { params: { size: 50, sort: 'slaDueAt,asc' } })
      .then((r) => setRows(r.data.content)).catch((e) => setErr(errMsg(e)));
  }, []);

  if (!rows) return err ? <Alert>{err}</Alert> : <div className="loading">Loading your work…</div>;

  const toAccept = rows.filter((c) => c.status === 'ASSIGNED' && !c.acceptedAt);
  const toStart = rows.filter((c) => c.status === 'ASSIGNED' && c.acceptedAt);
  const inProgress = rows.filter((c) => c.status === 'IN_PROGRESS');
  const waiting = rows.filter((c) => c.status === 'RESOLUTION_SUBMITTED');
  const done = rows.filter((c) => c.status === 'RESOLVED');
  const late = rows.filter((c) => c.overdue).length;
  const pending = toAccept.length + toStart.length + inProgress.length + waiting.length;
  const cards = [['📥', 'New - to accept', toAccept.length], ['▶️', 'Accepted - to start', toStart.length], ['🛠️', 'In progress', inProgress.length],
    ['🕵️', 'Awaiting admin check', waiting.length], ['✅', 'Completed', done.length], ['⏰', 'Overdue', late]];
  const needAction = [...toAccept, ...toStart, ...inProgress];

  return (
    <>
      <div className="page-head"><div><h1>My work</h1><p className="muted">{user.departmentName ? `${user.departmentName} · ` : ''}{pending} open task(s)</p></div></div>
      <Alert>{err}</Alert>
      <div className="grid grid-stats">
        {cards.map(([ico, lbl, n]) => <div key={lbl} className="card stat"><div className="ico">{ico}</div><div><div className="num">{n}</div><div className="lbl">{lbl}</div></div></div>)}
      </div>
      <div className="card mt"><h2>Needs your action</h2><TaskTable rows={needAction} empty="Nothing waiting for you right now. 🎉" /></div>
      {waiting.length > 0 && <div className="card mt"><h2>Submitted - waiting for admin verification</h2><TaskTable rows={waiting} empty="" /></div>}
      <div className="card mt"><div className="row between"><h2>Recently completed</h2><Link to="/complaints?status=RESOLVED">All completed →</Link></div><TaskTable rows={done.slice(0, 5)} empty="No completed complaints yet." /></div>
    </>
  );
}
