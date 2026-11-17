import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ResponsiveContainer, PieChart, Pie, Cell, Tooltip, Legend, BarChart, Bar, XAxis, YAxis, CartesianGrid, LineChart, Line } from 'recharts';
import api, { download, errMsg } from '../api/client';
import Alert from '../components/Alert';
import MapView from '../components/MapView';
import { StatusBadge, PriorityBadge } from '../components/Badges';
import { STATUS_COLORS, CATEGORIES, label, fmtDate } from '../constants';

const toArr = (obj, fmt = label) => Object.entries(obj || {}).map(([k, v]) => ({ name: fmt(k), key: k, value: v }));
const EMPTY = { from: '', to: '', departmentId: '', category: '' };

export default function AdminDashboard() {
  const [filters, setFilters] = useState(EMPTY);
  const [applied, setApplied] = useState(EMPTY);
  const [d, setD] = useState(null);
  const [departments, setDepartments] = useState([]);
  const [review, setReview] = useState([]);
  const [verify, setVerify] = useState([]);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState('');

  const params = () => Object.fromEntries(Object.entries(applied).filter(([, v]) => v));

  useEffect(() => { api.get('/departments').then((r) => setDepartments(r.data)).catch(() => {}); }, []);
  useEffect(() => {
    Promise.all([api.get('/complaints', { params: { status: 'SUBMITTED', size: 5, sort: 'createdAt,asc' } }),
      api.get('/complaints', { params: { status: 'RESOLUTION_SUBMITTED', size: 5, sort: 'updatedAt,asc' } })])
      .then(([a, b]) => { setReview(a.data.content); setVerify(b.data.content); }).catch(() => {});
  }, []);
  useEffect(() => {
    setD(null);
    api.get('/analytics/summary', { params: params() }).then((r) => { setD(r.data); setErr(''); }).catch((e) => setErr(errMsg(e)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applied]);

  const exportFile = async (kind) => {
    setBusy(kind); setErr('');
    try { await download(`/analytics/export/${kind === 'pdf' ? 'pdf' : 'excel'}`, params(), kind === 'pdf' ? 'civictrack-report.pdf' : 'civictrack-report.xlsx'); }
    catch (e) { setErr(errMsg(e)); }
    setBusy('');
  };

  const set = (k) => (e) => setFilters({ ...filters, [k]: e.target.value });

  return (
    <>
      <div className="page-head">
        <div><h1>Admin dashboard</h1><p className="muted">Live analytics from the complaint database</p></div>
        <div className="row">
          <button className="btn" disabled={!!busy} onClick={() => exportFile('excel')}>{busy === 'excel' ? 'Preparing…' : '📗 Export Excel'}</button>
          <button className="btn" disabled={!!busy} onClick={() => exportFile('pdf')}>{busy === 'pdf' ? 'Preparing…' : '📕 Export PDF'}</button>
        </div>
      </div>
      <Alert onClose={() => setErr('')}>{err}</Alert>

      <form className="card toolbar" onSubmit={(e) => { e.preventDefault(); setApplied(filters); }}>
        <div><label>From</label><input type="date" value={filters.from} onChange={set('from')} /></div>
        <div><label>To</label><input type="date" value={filters.to} onChange={set('to')} /></div>
        <div><label>Department</label><select value={filters.departmentId} onChange={set('departmentId')}><option value="">All</option>{departments.map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}</select></div>
        <div><label>Category</label><select value={filters.category} onChange={set('category')}><option value="">All</option>{CATEGORIES.map((x) => <option key={x} value={x}>{label(x)}</option>)}</select></div>
        <div className="row" style={{ alignItems: 'flex-end' }}><button className="btn btn-primary">Apply</button><button type="button" className="btn" onClick={() => { setFilters(EMPTY); setApplied(EMPTY); }}>Reset</button></div>
      </form>

      {!d ? <div className="loading mt">Loading analytics…</div> : <Body d={d} review={review} verify={verify} />}
    </>
  );
}

function Body({ d, review, verify }) {
  const cards = [
    ['📋', 'Total complaints', d.total], ['🆕', 'Pending (new)', d.pending], ['🔍', 'Under review', d.underReview], ['📌', 'Assigned', d.assigned],
    ['🛠️', 'In progress', d.inProgress], ['🕵️', 'Awaiting verification', d.resolutionSubmitted], ['✅', 'Resolved', d.resolved], ['🚫', 'Rejected', d.rejected],
    ['♻️', 'Reopened', d.reopened], ['⏰', 'Overdue (SLA)', d.overdue], ['⏱️', 'Avg. resolution (hrs)', d.avgResolutionHours], ['⭐', 'Avg. citizen rating', d.avgRating || '—'],
  ];
  const statusData = toArr(d.byStatus).filter((x) => x.value > 0);
  const markers = d.points.map((p) => ({ lat: p.latitude, lng: p.longitude, title: p.title, subtitle: `${p.trackingId} · ${label(p.status)}`, link: `/complaints/${p.id}` }));

  return (
    <>
      <div className="grid grid-stats mt">
        {cards.map(([ico, lbl, n]) => <div key={lbl} className="card stat"><div className="ico">{ico}</div><div><div className="num">{n}</div><div className="lbl">{lbl}</div></div></div>)}
      </div>

      <div className="grid grid-2 mt">
        <div className="card">
          <div className="row between"><h2>Needs review</h2><Link to="/complaints?status=SUBMITTED">All new →</Link></div>
          {review.length === 0 ? <div className="empty">No new complaints waiting.</div> : review.map((c) => (
            <div key={c.id} className="note-row"><Link to={`/complaints/${c.id}`}><strong>{c.trackingId}</strong> · {c.title}</Link><div><PriorityBadge priority={c.priority} /> <small className="muted">{c.departmentName || 'No department'} · {fmtDate(c.createdAt)}</small></div></div>
          ))}
        </div>
        <div className="card">
          <div className="row between"><h2>Resolutions to verify</h2><Link to="/complaints?status=RESOLUTION_SUBMITTED">All →</Link></div>
          {verify.length === 0 ? <div className="empty">Nothing waiting for verification.</div> : verify.map((c) => (
            <div key={c.id} className="note-row"><Link to={`/complaints/${c.id}`}><strong>{c.trackingId}</strong> · {c.title}</Link><div><StatusBadge status={c.status} /> <small className="muted">{c.officerName} · {fmtDate(c.updatedAt)}</small></div></div>
          ))}
        </div>
      </div>
      {d.overdue > 0 && <div className="alert alert-error mt">⏰ {d.overdue} complaint(s) are past their SLA deadline. <Link to="/complaints?overdue=1">Show overdue complaints →</Link></div>}

      <div className="grid grid-2 mt">
        <div className="card"><h2>Complaints by status</h2><div className="chart-box">
          {statusData.length === 0 ? <div className="empty">No data yet</div> : (
            <ResponsiveContainer><PieChart>
              <Pie data={statusData} dataKey="value" nameKey="name" innerRadius={55} outerRadius={95} paddingAngle={2}>{statusData.map((e) => <Cell key={e.key} fill={STATUS_COLORS[e.key]} />)}</Pie><Tooltip /><Legend />
            </PieChart></ResponsiveContainer>
          )}
        </div></div>
        <div className="card"><h2>Monthly trend</h2><div className="chart-box">
          <ResponsiveContainer><LineChart data={d.monthly}>
            <CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="month" fontSize={12} /><YAxis allowDecimals={false} fontSize={12} /><Tooltip /><Legend />
            <Line type="monotone" dataKey="submitted" name="Submitted" stroke="#15803d" strokeWidth={2} />
            <Line type="monotone" dataKey="resolved" name="Resolved" stroke="#16a34a" strokeWidth={2} />
          </LineChart></ResponsiveContainer>
        </div></div>
        <div className="card"><h2>Complaints by category</h2><div className="chart-box">
          <ResponsiveContainer><BarChart data={toArr(d.byCategory)}>
            <CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="name" fontSize={10} interval={0} angle={-25} textAnchor="end" height={70} /><YAxis allowDecimals={false} fontSize={12} /><Tooltip />
            <Bar dataKey="value" name="Complaints" fill="#22c55e" radius={[4, 4, 0, 0]} />
          </BarChart></ResponsiveContainer>
        </div></div>
        <div className="card"><h2>Complaints by department</h2><div className="chart-box">
          <ResponsiveContainer><BarChart data={toArr(d.byDepartment, (x) => x)} layout="vertical" margin={{ left: 30 }}>
            <CartesianGrid strokeDasharray="3 3" /><XAxis type="number" allowDecimals={false} fontSize={12} /><YAxis type="category" dataKey="name" width={150} fontSize={11} /><Tooltip />
            <Bar dataKey="value" name="Complaints" fill="#0d9488" radius={[0, 4, 4, 0]} />
          </BarChart></ResponsiveContainer>
        </div></div>
      </div>

      <div className="card mt"><h2>Department statistics</h2>
        <div className="table-wrap"><table>
          <thead><tr><th>Department</th><th>Total</th><th>Resolved</th><th>Open</th><th>Overdue</th><th>Avg. resolution (h)</th></tr></thead>
          <tbody>{d.departmentStats.map((x) => <tr key={x.department}><td><strong>{x.department}</strong></td><td>{x.total}</td><td>{x.resolved}</td><td>{x.open}</td><td>{x.overdue}</td><td>{x.avgResolutionHours}</td></tr>)}</tbody>
        </table></div>
        {d.departmentStats.length === 0 && <div className="empty">No data for the selected filters.</div>}
      </div>

      <div className="card mt"><h2>Officer performance</h2>
        <div className="table-wrap"><table>
          <thead><tr><th>Officer</th><th>Department</th><th>Assigned</th><th>In progress</th><th>Resolved</th><th>Overdue</th><th>Avg. resolution (h)</th></tr></thead>
          <tbody>{d.officerPerformance.map((x) => <tr key={x.officer + x.department}><td><strong>{x.officer}</strong></td><td>{x.department}</td><td>{x.assigned}</td><td>{x.inProgress}</td><td>{x.resolved}</td><td>{x.overdue}</td><td>{x.avgResolutionHours}</td></tr>)}</tbody>
        </table></div>
        {d.officerPerformance.length === 0 && <div className="empty">No officer has been assigned a complaint yet.</div>}
      </div>

      <div className="card mt"><h2>Complaint map</h2>{markers.length ? <MapView markers={markers} height={400} /> : <div className="empty">No located complaints yet.</div>}</div>
    </>
  );
}
