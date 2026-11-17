import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errMsg } from '../api/client';
import Alert from '../components/Alert';
import Pagination from '../components/Pagination';
import { fmtDate, label } from '../constants';

export default function Notifications() {
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [err, setErr] = useState('');

  const load = useCallback(() => {
    api.get('/notifications', { params: { page, size: 15 } }).then((r) => setData(r.data)).catch((e) => setErr(errMsg(e)));
  }, [page]);
  useEffect(() => { load(); }, [load]);

  const readOne = async (n) => { if (!n.read) { await api.post(`/notifications/${n.id}/read`).catch(() => {}); load(); } };
  const readAll = async () => { await api.post('/notifications/read-all').catch((e) => setErr(errMsg(e))); load(); };
  const unread = data ? data.content.filter((n) => !n.read).length : 0;

  return (
    <>
      <div className="page-head"><div><h1>Notifications</h1><p className="muted">Your complete notification history</p></div>
        <button className="btn" onClick={readAll} disabled={!unread}>Mark all as read</button></div>
      <Alert>{err}</Alert>
      <div className="card">
        {!data ? <div className="loading">Loading…</div> : data.content.length === 0 ? <div className="empty">No notifications yet.</div> : data.content.map((n) => (
          <div key={n.id} className={`note-row ${n.read ? '' : 'unread'}`} onClick={() => readOne(n)}>
            <div>{!n.read && <span className="dot-unread" />}{n.message}</div>
            <small className="muted">{label(n.type)} · {fmtDate(n.createdAt)}{n.complaintId && <> · <Link to={`/complaints/${n.complaintId}`}>{n.trackingId}</Link></>}</small>
          </div>
        ))}
        {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
      </div>
    </>
  );
}
