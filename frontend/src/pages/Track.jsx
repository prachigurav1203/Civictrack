import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { errMsg } from '../api/client';
import { useAuth } from '../context/AuthContext';
import Alert from '../components/Alert';
import MapView from '../components/MapView';
import Timeline from '../components/Timeline';
import Photos from '../components/Photos';
import StatusStepper from '../components/StatusStepper';
import { ResolutionList } from './ComplaintDetail';
import { StatusBadge, PriorityBadge } from '../components/Badges';
import { label, fmtDate } from '../constants';

export default function Track() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const [id, setId] = useState(params.get('id') || '');
  const [c, setC] = useState(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const lookup = async (tid) => {
    if (!tid.trim()) return;
    setBusy(true); setErr(''); setC(null);
    try { setC((await api.get(`/complaints/track/${encodeURIComponent(tid.trim())}`)).data); }
    catch (ex) { setErr(errMsg(ex)); }
    setBusy(false);
  };

  useEffect(() => { if (params.get('id')) lookup(params.get('id')); /* eslint-disable-next-line */ }, []);

  const submit = (e) => { e.preventDefault(); setParams({ id: id.trim() }); lookup(id); };

  return (
    <div>
      <div className="public-bar">
        <span className="logo">📍 CivicTrack</span>
        <Link className="btn btn-sm" to={user ? '/dashboard' : '/login'}>{user ? 'Dashboard' : 'Sign in'}</Link>
      </div>
      <div className="public-content">
        <h1>Track your complaint</h1>
        <p className="muted">Enter the tracking ID you received when you registered the complaint (e.g. CT2026-ABC123).</p>
        <form className="row mt" onSubmit={submit}>
          <input style={{ flex: 1, minWidth: 220 }} placeholder="Tracking ID" value={id} onChange={(e) => setId(e.target.value)} />
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Searching…' : 'Track'}</button>
        </form>
        <div className="mt"><Alert>{err}</Alert></div>
        {c && (
          <div className="grid grid-detail mt">
            <div className="card">
              <div className="row between"><h2>{c.title}</h2><StatusBadge status={c.status} /></div>
              <p className="tracking-id">{c.trackingId}</p>
              <p>{c.description}</p>
              <div className="meta">
                <div><small>Category</small>{label(c.category)}</div>
                <div><small>Priority</small><PriorityBadge priority={c.priority} /></div>
                <div><small>Department</small>{c.departmentName || 'Not assigned yet'}</div>
                <div><small>Registered</small>{fmtDate(c.createdAt)}</div>
                <div><small>Last updated</small>{fmtDate(c.updatedAt)}</div>
                {c.resolvedAt && <div><small>Resolved</small>{fmtDate(c.resolvedAt)}</div>}
              </div>
              <Photos urls={c.photos} caption="Complaint photos" />
              {c.latitude != null && <div className="mt"><MapView markers={[{ lat: c.latitude, lng: c.longitude, title: c.title }]} height={260} />{c.address && <small className="muted">{c.address}</small>}</div>}
            </div>
            <div>
              <div className="card"><h2>Status history</h2><Timeline history={c.history} /></div>
            </div>
          </div>
        )}
        {c && (
          <div className="mt">
            <div className="card"><StatusStepper status={c.status} /></div>
            <ResolutionList items={c.resolutions} staff={false} />
          </div>
        )}
      </div>
    </div>
  );
}
