import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import api, { errMsg } from '../api/client';
import { useAuth } from '../context/AuthContext';
import Alert from '../components/Alert';
import MapView from '../components/MapView';
import Timeline from '../components/Timeline';
import Photos from '../components/Photos';
import Stars from '../components/Stars';
import StatusStepper from '../components/StatusStepper';
import { StatusBadge, PriorityBadge, SlaBadge } from '../components/Badges';
import { PRIORITIES, label, fmtDate } from '../constants';

const ASSIGNABLE = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED'];

export function ResolutionList({ items = [], staff }) {
  if (items.length === 0) return null;
  return (
    <div className="card mt">
      <h2>Resolution details</h2>
      {items.map((r) => (
        <div key={r.id} className={`resolution res-${r.status}`}>
          <div className="row between">
            <strong>{staff ? `Submitted by ${r.submittedBy || 'officer'}` : 'Work completed'} · {fmtDate(r.submittedAt)}</strong>
            {staff && <span className={`badge ${r.status === 'APPROVED' ? 'st-RESOLVED' : r.status === 'REJECTED' ? 'st-REJECTED' : 'st-RESOLUTION_SUBMITTED'}`}>{label(r.status)}</span>}
          </div>
          <p style={{ whiteSpace: 'pre-wrap' }}>{r.remarks}</p>
          <Photos urls={r.photos} caption="Work completion photos" />
          {r.verifiedAt && <small className="muted">{r.status === 'APPROVED' ? 'Verified' : 'Rejected'} by {r.verifiedBy || 'admin'} on {fmtDate(r.verifiedAt)}{r.adminRemarks ? ` - ${r.adminRemarks}` : ''}</small>}
        </div>
      ))}
    </div>
  );
}

// ------------------------------------------------------------------ admin
function AdminPanel({ c, act, busy }) {
  const [departments, setDepartments] = useState([]);
  const [officers, setOfficers] = useState([]);
  const [dept, setDept] = useState(String(c.departmentId || c.suggestedDepartmentId || ''));
  const [officer, setOfficer] = useState(String(c.officerId || ''));
  const [note, setNote] = useState('');
  const [reason, setReason] = useState('');
  const [verifyNote, setVerifyNote] = useState('');
  const [priority, setPriority] = useState(c.priority);
  const [dups, setDups] = useState(null);
  const canAssign = ASSIGNABLE.includes(c.status);
  const closed = c.status === 'RESOLVED' || c.status === 'REJECTED';

  useEffect(() => { api.get('/departments').then((r) => setDepartments(r.data)).catch(() => {}); }, []);
  useEffect(() => { setDept(String(c.departmentId || c.suggestedDepartmentId || '')); setOfficer(String(c.officerId || '')); setPriority(c.priority); }, [c.id, c.departmentId, c.officerId, c.priority, c.suggestedDepartmentId]);
  useEffect(() => {
    if (!dept) { setOfficers([]); return; }
    api.get('/admin/officers', { params: { departmentId: dept } }).then((r) => setOfficers(r.data)).catch(() => setOfficers([]));
  }, [dept]);
  useEffect(() => { api.get(`/complaints/${c.id}/duplicates`).then((r) => setDups(r.data)).catch(() => setDups([])); }, [c.id]);

  const post = (path, body, ok) => act(() => api.post(`/complaints/${c.id}/${path}`, body), ok);

  return (
    <>
      {c.status === 'RESOLUTION_SUBMITTED' && (
        <div className="card verify-card">
          <h2>Verify the submitted resolution</h2>
          <p className="muted">Review the officer's remarks and photos, then approve or reject.</p>
          <div className="field"><label>Remarks {` (required when rejecting)`}</label><textarea style={{ minHeight: 70 }} maxLength={1000} value={verifyNote} onChange={(e) => setVerifyNote(e.target.value)} /></div>
          <div className="row">
            <button className="btn btn-success" disabled={busy} onClick={() => post('verify', { approve: true, remarks: verifyNote }, 'Resolution approved. The complaint is now RESOLVED.')}>✔ Approve</button>
            <button className="btn btn-danger" disabled={busy || !verifyNote.trim()} onClick={() => post('verify', { approve: false, remarks: verifyNote }, 'Resolution rejected. The officer must continue the work.')}>✖ Reject</button>
          </div>
        </div>
      )}

      {canAssign && (
        <form className="card mt" onSubmit={(e) => { e.preventDefault(); if (!dept || !officer) return; post('assign', { departmentId: Number(dept), officerId: Number(officer), remarks: note }, c.officerId ? 'Complaint reassigned.' : 'Officer assigned.'); }}>
          <h2>{c.officerId ? 'Reassign complaint' : 'Assign department & officer'}</h2>
          {c.suggestedDepartmentName && <p className="muted" style={{ marginTop: 0 }}>Suggested by category: <strong>{c.suggestedDepartmentName}</strong>{String(c.departmentId) === String(c.suggestedDepartmentId) ? ' (auto-assigned)' : ''}</p>}
          <div className="field"><label>Department</label>
            <select value={dept} onChange={(e) => { setDept(e.target.value); setOfficer(''); }}><option value="">Select department…</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
          <div className="field"><label>Officer *</label>
            <select value={officer} onChange={(e) => setOfficer(e.target.value)} disabled={!dept}><option value="">{officers.length ? 'Select officer…' : 'No active officer in this department'}</option>{officers.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}</select></div>
          <div className="field"><label>Note (optional)</label><input maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} /></div>
          <button className="btn btn-primary btn-block" disabled={busy || !dept || !officer}>{c.officerId ? 'Reassign' : 'Assign officer'}</button>
          {(c.status === 'SUBMITTED' || c.status === 'REOPENED') && <button type="button" className="btn btn-block mt" disabled={busy} onClick={() => post('review', {}, 'Marked as under review.')}>Mark as under review</button>}
        </form>
      )}

      {!closed && (
        <div className="card mt">
          <h2>Priority & SLA</h2>
          <div className="row"><select style={{ flex: 1 }} value={priority} onChange={(e) => setPriority(e.target.value)}>{PRIORITIES.map((p) => <option key={p} value={p}>{label(p)}</option>)}</select>
            <button className="btn" disabled={busy || priority === c.priority} onClick={() => post('priority', { priority }, 'Priority updated.')}>Save</button></div>
          <small className="muted">SLA: Critical 24 h · High 3 d · Medium 7 d · Low 14 d</small>
        </div>
      )}

      {(['SUBMITTED', 'UNDER_REVIEW', 'REOPENED', 'RESOLVED', 'REJECTED'].includes(c.status)) && (
        <div className="card mt">
          <h2>{closed ? 'Reopen' : 'Reject complaint'}</h2>
          <div className="field"><textarea style={{ minHeight: 60 }} maxLength={1000} placeholder="Reason (required, visible to the citizen)" value={reason} onChange={(e) => setReason(e.target.value)} /></div>
          <div className="row">
            {closed && <button className="btn" disabled={busy || !reason.trim()} onClick={() => post('reopen', { remarks: reason }, 'Complaint reopened.')}>♻ Reopen</button>}
            {!closed && <button className="btn btn-danger" disabled={busy || !reason.trim()} onClick={() => post('reject', { remarks: reason }, 'Complaint rejected.')}>🚫 Reject complaint</button>}
          </div>
        </div>
      )}

      <div className="card mt">
        <h2>Possible duplicates</h2>
        {dups === null ? <div className="loading" style={{ padding: '.5rem' }}>Checking…</div> : dups.length === 0 ? <p className="muted" style={{ margin: 0 }}>No similar complaint within 150 m.</p> : dups.map((d) => (
          <div key={d.id} className="note-row"><Link to={`/complaints/${d.id}`}><strong>{d.trackingId}</strong> · {d.title}</Link>
            <div><StatusBadge status={d.status} /> <small className="muted">{d.distanceMeters} m away · {d.similarityPercent}% text match</small></div></div>
        ))}
        <small className="muted">Suggestions only - the admin decides whether they are duplicates.</small>
      </div>
    </>
  );
}

// ---------------------------------------------------------------- officer
function OfficerPanel({ c, act, busy }) {
  const [note, setNote] = useState('');
  const [remarks, setRemarks] = useState('');
  const [files, setFiles] = useState([]);
  const post = (path, body, ok) => act(() => api.post(`/complaints/${c.id}/${path}`, body), ok);

  const pick = (e) => {
    const list = Array.from(e.target.files).filter((f) => f.type.startsWith('image/') && f.size <= 5 * 1024 * 1024).slice(0, 5);
    setFiles(list);
  };
  const submitResolution = async (e) => {
    e.preventDefault();
    const fd = new FormData();
    fd.append('remarks', remarks);
    files.forEach((f) => fd.append('photos', f));
    const ok = await act(() => api.post(`/complaints/${c.id}/resolution`, fd), 'Resolution submitted to the admin for verification.');
    if (ok) { setRemarks(''); setFiles([]); }
  };

  if (c.status === 'ASSIGNED' && !c.acceptedAt) return (
    <div className="card"><h2>New assignment</h2><p className="muted">Accept this complaint to start working on it.</p>
      <button className="btn btn-primary btn-block" disabled={busy} onClick={() => post('accept', null, 'Complaint accepted.')}>✔ Accept this work</button></div>
  );
  if (c.status === 'ASSIGNED') return (
    <div className="card"><h2>Start work</h2><div className="field"><input maxLength={500} placeholder="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} /></div>
      <button className="btn btn-primary btn-block" disabled={busy} onClick={() => post('start', { remarks: note }, 'Status changed to In Progress.')}>▶ Start work (In Progress)</button></div>
  );
  if (c.status === 'IN_PROGRESS') return (
    <>
      <div className="card"><h2>Add progress update</h2>
        <div className="field"><textarea style={{ minHeight: 70 }} maxLength={1000} placeholder="What has been done so far? (visible to the citizen)" value={note} onChange={(e) => setNote(e.target.value)} /></div>
        <button className="btn btn-block" disabled={busy || !note.trim()} onClick={async () => { if (await act(() => api.post(`/complaints/${c.id}/progress`, { remarks: note }), 'Progress update added.')) setNote(''); }}>Add update</button></div>
      <form className="card mt" onSubmit={submitResolution}>
        <h2>Submit completed work</h2>
        <p className="muted" style={{ marginTop: 0 }}>The admin will verify your proof before the complaint is marked resolved.</p>
        <div className="field"><label>Resolution remarks *</label><textarea required minLength={10} maxLength={2000} value={remarks} onChange={(e) => setRemarks(e.target.value)} placeholder="Describe the work completed" /></div>
        <div className="field"><label>Completion photos * (1-5, max 5 MB each)</label><input type="file" accept="image/png,image/jpeg,image/gif,image/webp" multiple onChange={pick} />
          {files.length > 0 && <small className="muted">{files.length} photo(s) selected</small>}</div>
        <button className="btn btn-primary btn-block" disabled={busy || files.length === 0 || remarks.trim().length < 10}>Submit for verification</button>
      </form>
    </>
  );
  if (c.status === 'RESOLUTION_SUBMITTED') return <div className="card"><h2>Awaiting verification</h2><p className="muted" style={{ margin: 0 }}>Your resolution was submitted. The admin will approve it or send it back with remarks.</p></div>;
  return null;
}

// ---------------------------------------------------------------- citizen
function FeedbackCard({ c, act, busy }) {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  if (c.status !== 'RESOLVED') return null;
  if (c.feedback) return <div className="card"><h2>Your feedback</h2><Stars value={c.feedback.rating} />{c.feedback.comment && <p>{c.feedback.comment}</p>}<small className="muted">Thank you! Submitted {fmtDate(c.feedback.createdAt)}</small></div>;
  return (
    <form className="card" onSubmit={(e) => { e.preventDefault(); if (rating) act(() => api.post(`/complaints/${c.id}/feedback`, { rating, comment }), 'Thank you for your feedback!'); }}>
      <h2>Rate the resolution</h2>
      <div className="field"><Stars value={rating} onChange={setRating} size="2rem" /></div>
      <div className="field"><textarea style={{ minHeight: 70 }} maxLength={1000} placeholder="Comments (optional)" value={comment} onChange={(e) => setComment(e.target.value)} /></div>
      <button className="btn btn-primary btn-block" disabled={busy || !rating}>Submit feedback</button>
    </form>
  );
}

// ------------------------------------------------------------------- page
export default function ComplaintDetail() {
  const { id } = useParams();
  const { state } = useLocation();
  const { user } = useAuth();
  const [c, setC] = useState(null);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { setC(null); api.get(`/complaints/${id}`).then((r) => setC(r.data)).catch((e) => setErr(errMsg(e))); }, [id]);

  const act = useCallback(async (fn, okMsg) => {
    setBusy(true); setErr(''); setMsg('');
    try { const r = await fn(); if (r?.data?.id) setC(r.data); setMsg(okMsg); setBusy(false); return true; }
    catch (e) { setErr(errMsg(e)); setBusy(false); return false; }
  }, []);

  if (!c) return err ? <><Alert>{err}</Alert><Link to="/complaints">← Back</Link></> : <div className="loading">Loading…</div>;
  const staff = user.role !== 'CITIZEN';

  return (
    <>
      <div className="page-head"><div><Link to="/complaints">← Back to complaints</Link></div></div>
      {state?.created && (
        <div className="tracking-banner">
          ✅ <strong>Complaint registered successfully.</strong> Your Complaint ID is <span className="tracking-id">{state.created}</span>. It was routed to <strong>{c.departmentName || 'the municipality'}</strong>.
          Follow it any time at <code>{window.location.origin}/track?id={state.created}</code>
        </div>
      )}
      <Alert onClose={() => setErr('')}>{err}</Alert>
      <Alert type="success" onClose={() => setMsg('')}>{msg}</Alert>

      <div className="card" style={{ marginBottom: '1rem' }}><StatusStepper status={c.status} /></div>
      {c.overdue && staff && <div className="alert alert-error">⏰ SLA breached: this complaint is past its deadline ({fmtDate(c.slaDueAt)}).</div>}

      <div className="grid grid-detail">
        <div>
          <div className="card">
            <div className="row between"><h1 style={{ fontSize: '1.35rem' }}>{c.title}</h1><StatusBadge status={c.status} /></div>
            <p className="tracking-id">{c.trackingId}</p>
            <p style={{ whiteSpace: 'pre-wrap' }}>{c.description}</p>
            <div className="meta">
              <div><small>Category</small>{label(c.category)}</div>
              <div><small>Priority</small><PriorityBadge priority={c.priority} /></div>
              <div><small>Reported by</small>{c.citizenName}</div>
              <div><small>Department</small>{c.departmentName || 'Not assigned'}</div>
              <div><small>Assigned officer</small>{c.officerName || '—'}</div>
              {staff && <div><small>SLA</small><SlaBadge c={c} /></div>}
              <div><small>Created</small>{fmtDate(c.createdAt)}</div>
              <div><small>Last updated</small>{fmtDate(c.updatedAt)}</div>
              {c.acceptedAt && <div><small>Accepted by officer</small>{fmtDate(c.acceptedAt)}</div>}
              {c.resolvedAt && <div><small>Resolved</small>{fmtDate(c.resolvedAt)}</div>}
            </div>
            <Photos urls={c.photos} caption="Complaint photos" />
          </div>

          <ResolutionList items={c.resolutions} staff={staff} />

          {c.latitude != null && (
            <div className="card mt">
              <h2>Location</h2>
              <MapView markers={[{ lat: c.latitude, lng: c.longitude, title: c.title }]} height={300} />
              <p className="muted" style={{ marginBottom: 0 }}>{c.address || 'No address provided'} ({c.latitude.toFixed(5)}, {c.longitude.toFixed(5)}) ·{' '}
                <a href={`https://www.openstreetmap.org/?mlat=${c.latitude}&mlon=${c.longitude}#map=17/${c.latitude}/${c.longitude}`} target="_blank" rel="noreferrer">Open in OpenStreetMap</a></p>
            </div>
          )}
        </div>

        <div>
          {user.role === 'ADMIN' && <AdminPanel c={c} act={act} busy={busy} />}
          {user.role === 'OFFICER' && <OfficerPanel c={c} act={act} busy={busy} />}
          {user.role === 'CITIZEN' && <FeedbackCard c={c} act={act} busy={busy} />}
          <div className="card mt"><h2>Status history</h2><Timeline history={c.history} /></div>
        </div>
      </div>
    </>
  );
}
