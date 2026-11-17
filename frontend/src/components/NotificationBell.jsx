import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api/client';
import { fmtDate } from '../constants';

export default function NotificationBell() {
  const nav = useNavigate();
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const box = useRef(null);

  const loadCount = useCallback(() => {
    api.get('/notifications/unread-count').then((r) => setCount(r.data.count)).catch(() => {});
  }, []);

  useEffect(() => { loadCount(); const t = setInterval(loadCount, 30000); return () => clearInterval(t); }, [loadCount]);

  useEffect(() => {
    if (!open) return undefined;
    api.get('/notifications', { params: { size: 6 } }).then((r) => setItems(r.data.content)).catch(() => {});
    const close = (e) => { if (box.current && !box.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  const openItem = async (n) => {
    setOpen(false);
    if (!n.read) { try { await api.post(`/notifications/${n.id}/read`); } catch { /* ignore */ } loadCount(); }
    if (n.complaintId) nav(`/complaints/${n.complaintId}`);
  };
  const readAll = async () => { await api.post('/notifications/read-all').catch(() => {}); setItems(items.map((i) => ({ ...i, read: true }))); setCount(0); };

  return (
    <div className="bell" ref={box}>
      <button className="bell-btn" onClick={() => setOpen(!open)} aria-label="Notifications">
        🔔{count > 0 && <span className="bell-count">{count > 99 ? '99+' : count}</span>}
      </button>
      {open && (
        <div className="bell-menu">
          <div className="row between bell-head"><strong>Notifications</strong>{count > 0 && <button className="link-btn" onClick={readAll}>Mark all read</button>}</div>
          {items.length === 0 ? <div className="empty" style={{ padding: '1rem' }}>No notifications yet.</div> : items.map((n) => (
            <button key={n.id} className={`bell-item ${n.read ? '' : 'unread'}`} onClick={() => openItem(n)}>
              <span>{n.message}</span><small className="muted">{fmtDate(n.createdAt)}</small>
            </button>
          ))}
          <Link className="bell-foot" to="/notifications" onClick={() => setOpen(false)}>View all notifications →</Link>
        </div>
      )}
    </div>
  );
}
