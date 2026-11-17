import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { errMsg } from '../api/client';
import { useAuth } from '../context/AuthContext';
import Alert from '../components/Alert';
import Pagination from '../components/Pagination';
import { StatusBadge, PriorityBadge, SlaBadge } from '../components/Badges';
import { STATUSES, CATEGORIES, PRIORITIES, label, fmtDate } from '../constants';

const SORTS = [
  ['createdAt,desc', 'Newest first'], ['createdAt,asc', 'Oldest first'], ['updatedAt,desc', 'Recently updated'],
  ['slaDueAt,asc', 'SLA deadline (soonest)'], ['title,asc', 'Title (A-Z)'],
];

export default function Complaints() {
  const { user } = useAuth();
  const [qs] = useSearchParams();
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [f, setF] = useState({ status: qs.get('status') || '', category: '', priority: '', departmentId: '', officerId: '', from: '', to: '',
    overdue: qs.get('overdue') === '1', sort: 'createdAt,desc' });
  const [departments, setDepartments] = useState([]);
  const [officers, setOfficers] = useState([]);
  const isAdmin = user.role === 'ADMIN';

  // keep filters in sync when the sidebar/dashboard links change the query string
  useEffect(() => { setF((x) => ({ ...x, status: qs.get('status') || '', overdue: qs.get('overdue') === '1' })); setPage(0); }, [qs]);
  useEffect(() => {
    if (!isAdmin) return;
    api.get('/departments').then((r) => setDepartments(r.data)).catch(() => {});
    api.get('/admin/officers').then((r) => setOfficers(r.data)).catch(() => {});
  }, [isAdmin]);

  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput.trim()); setPage(0); }, 400);
    return () => clearTimeout(t);
  }, [searchInput]);

  useEffect(() => {
    setLoading(true);
    const params = { page, size: 10, sort: f.sort };
    if (search) params.search = search;
    ['status', 'category', 'priority', 'departmentId', 'officerId', 'from', 'to'].forEach((k) => { if (f[k]) params[k] = f[k]; });
    if (f.overdue) params.overdue = true;
    api.get('/complaints', { params })
      .then((r) => { setData(r.data); setErr(''); })
      .catch((e) => setErr(errMsg(e)))
      .finally(() => setLoading(false));
  }, [page, search, f]);

  const setFilter = (k) => (e) => { setF({ ...f, [k]: e.target.value }); setPage(0); };
  const title = { CITIZEN: 'My complaints', ADMIN: 'All complaints', OFFICER: 'My assigned complaints' }[user.role];
  const clear = () => { setF({ status: '', category: '', priority: '', departmentId: '', officerId: '', from: '', to: '', overdue: false, sort: 'createdAt,desc' }); setSearchInput(''); setPage(0); };

  return (
    <>
      <div className="page-head">
        <div><h1>{title}</h1><p className="muted">{data ? `${data.totalElements} complaint(s) found` : ' '}</p></div>
        {user.role === 'CITIZEN' && <Link className="btn btn-primary" to="/complaints/new">➕ New complaint</Link>}
      </div>
      <div className="card">
        <div className="toolbar">
          <input className="grow" placeholder="🔍 Search by Complaint ID, title, address…" value={searchInput} onChange={(e) => setSearchInput(e.target.value)} />
          <select value={f.status} onChange={setFilter('status')}><option value="">All statuses</option>{STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}</select>
          <select value={f.category} onChange={setFilter('category')}><option value="">All categories</option>{CATEGORIES.map((s) => <option key={s} value={s}>{label(s)}</option>)}</select>
          {user.role !== 'CITIZEN' && <select value={f.priority} onChange={setFilter('priority')}><option value="">All priorities</option>{PRIORITIES.map((s) => <option key={s} value={s}>{label(s)}</option>)}</select>}
          {isAdmin && <select value={f.departmentId} onChange={setFilter('departmentId')}><option value="">All departments</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select>}
          {isAdmin && <select value={f.officerId} onChange={setFilter('officerId')}><option value="">All officers</option>{officers.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}</select>}
        </div>
        <div className="toolbar">
          <div><label>Created from</label><input type="date" value={f.from} onChange={setFilter('from')} /></div>
          <div><label>to</label><input type="date" value={f.to} onChange={setFilter('to')} /></div>
          <div><label>Sort</label><select value={f.sort} onChange={setFilter('sort')}>{SORTS.map(([v, t]) => <option key={v} value={v}>{t}</option>)}</select></div>
          {user.role !== 'CITIZEN' && <label className="check"><input type="checkbox" checked={f.overdue} onChange={(e) => { setF({ ...f, overdue: e.target.checked }); setPage(0); }} /> Overdue only</label>}
          <button className="btn" onClick={clear}>Clear filters</button>
        </div>
        <Alert>{err}</Alert>
        {loading && !data ? <div className="loading">Loading…</div> : data && data.content.length === 0 ? <div className="empty">No complaints match your filters.</div> : data && (
          <div className="table-wrap" style={{ opacity: loading ? 0.6 : 1 }}>
            <table>
              <thead><tr><th>Complaint ID</th><th>Title</th><th>Category</th>{user.role !== 'CITIZEN' && <th>Priority</th>}<th>Status</th>{user.role !== 'CITIZEN' && <th>SLA</th>}{isAdmin && <th>Citizen</th>}<th>Department</th>{user.role !== 'OFFICER' && <th>Officer</th>}<th>Created</th><th /></tr></thead>
              <tbody>{data.content.map((c) => (
                <tr key={c.id}>
                  <td><Link to={`/complaints/${c.id}`}><strong>{c.trackingId}</strong></Link></td>
                  <td className="wrap">{c.title}</td>
                  <td>{label(c.category)}</td>
                  {user.role !== 'CITIZEN' && <td><PriorityBadge priority={c.priority} /></td>}
                  <td><StatusBadge status={c.status} /></td>
                  {user.role !== 'CITIZEN' && <td><SlaBadge c={c} /></td>}
                  {isAdmin && <td>{c.citizenName}</td>}
                  <td>{c.departmentName || <span className="muted">Unassigned</span>}</td>
                  {user.role !== 'OFFICER' && <td>{c.officerName || <span className="muted">—</span>}</td>}
                  <td>{fmtDate(c.createdAt)}</td>
                  <td><Link className="btn btn-sm" to={`/complaints/${c.id}`}>View</Link></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
      </div>
    </>
  );
}
