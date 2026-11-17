import { useEffect, useState } from 'react';
import api, { errMsg } from '../api/client';
import { useAuth } from '../context/AuthContext';
import Alert from '../components/Alert';
import { label, fmtDate } from '../constants';

const EMPTY = { name: '', email: '', phone: '', password: '', departmentId: '' };
const TABS = [['CITIZEN', 'Citizens'], ['OFFICER', 'Officers'], ['ADMIN', 'Admins']];

export default function Users() {
  const { user: me } = useAuth();
  const [tab, setTab] = useState('OFFICER');
  const [search, setSearch] = useState('');
  const [users, setUsers] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [edit, setEdit] = useState(null);       // officer being edited
  const [pwd, setPwd] = useState(null);         // { id, name, value }
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');

  const load = () => api.get('/admin/users', { params: { role: tab, search: search || undefined } }).then((r) => setUsers(r.data)).catch((e) => setErr(errMsg(e)));
  useEffect(() => { const t = setTimeout(load, 250); return () => clearTimeout(t); /* eslint-disable-next-line */ }, [tab, search]);
  useEffect(() => { api.get('/departments').then((r) => setDepartments(r.data)).catch(() => {}); }, []);

  const run = async (fn, ok, after) => { setErr(''); setMsg(''); try { await fn(); setMsg(ok); after?.(); load(); } catch (ex) { setErr(errMsg(ex)); } };
  const create = (e) => { e.preventDefault(); run(() => api.post('/admin/officers', { ...form, departmentId: Number(form.departmentId) }), 'Officer account created. Share the credentials securely.', () => setForm(EMPTY)); };
  const saveEdit = (e) => { e.preventDefault(); run(() => api.put(`/admin/officers/${edit.id}`, { name: edit.name, phone: edit.phone || '', departmentId: Number(edit.departmentId) }), 'Officer updated.', () => setEdit(null)); };
  const resetPwd = (e) => { e.preventDefault(); run(() => api.post(`/admin/officers/${pwd.id}/password`, { newPassword: pwd.value }), 'Password reset.', () => setPwd(null)); };
  const toggle = (u) => run(() => api.patch(`/admin/users/${u.id}/active`, { active: !u.active }), u.active ? 'Account deactivated.' : 'Account activated.');
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  return (
    <>
      <div className="page-head"><div><h1>Users &amp; officers</h1><p className="muted">View citizens, create and manage officers, deactivate accounts</p></div></div>
      <Alert onClose={() => setErr('')}>{err}</Alert>
      <Alert type="success" onClose={() => setMsg('')}>{msg}</Alert>
      <div className="grid grid-detail">
        <div className="card">
          <div className="tabs">{TABS.map(([k, t]) => <button key={k} className={tab === k ? 'active' : ''} onClick={() => setTab(k)}>{t}</button>)}</div>
          <div className="toolbar"><input placeholder="🔍 Search name or email" value={search} onChange={(e) => setSearch(e.target.value)} /></div>
          <div className="table-wrap"><table>
            <thead><tr><th>Name</th><th>Email</th>{tab === 'OFFICER' && <th>Department</th>}<th>Joined</th><th>Status</th><th /></tr></thead>
            <tbody>{users.map((u) => (
              <tr key={u.id}><td><strong>{u.name}</strong><div className="muted" style={{ fontSize: '.8rem' }}>{u.phone}</div></td><td>{u.email}</td>{tab === 'OFFICER' && <td>{u.departmentName || '—'}</td>}<td>{fmtDate(u.createdAt)}</td>
                <td><span className={`badge ${u.active ? 'st-RESOLVED' : 'st-REJECTED'}`}>{u.active ? 'Active' : 'Disabled'}</span></td>
                <td className="row" style={{ flexWrap: 'nowrap' }}>
                  {u.role === 'OFFICER' && <><button className="btn btn-sm" onClick={() => setEdit({ id: u.id, name: u.name, phone: u.phone || '', departmentId: u.departmentId || '' })}>Edit</button>
                    <button className="btn btn-sm" onClick={() => setPwd({ id: u.id, name: u.name, value: '' })}>Password</button></>}
                  {u.id !== me.id && <button className="btn btn-sm" onClick={() => toggle(u)}>{u.active ? 'Deactivate' : 'Activate'}</button>}</td></tr>
            ))}</tbody>
          </table></div>
          {users.length === 0 && <div className="empty">No {label(tab).toLowerCase()} found.</div>}
        </div>

        <div>
          {edit ? (
            <form className="card" onSubmit={saveEdit}>
              <h2>Edit officer</h2>
              <div className="field"><label>Name</label><input required value={edit.name} onChange={(e) => setEdit({ ...edit, name: e.target.value })} /></div>
              <div className="field"><label>Phone</label><input maxLength={20} value={edit.phone} onChange={(e) => setEdit({ ...edit, phone: e.target.value })} /></div>
              <div className="field"><label>Department</label><select required value={edit.departmentId} onChange={(e) => setEdit({ ...edit, departmentId: e.target.value })}><option value="">Select…</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
              <div className="row"><button className="btn btn-primary">Save</button><button type="button" className="btn" onClick={() => setEdit(null)}>Cancel</button></div>
            </form>
          ) : pwd ? (
            <form className="card" onSubmit={resetPwd}>
              <h2>Reset password: {pwd.name}</h2>
              <div className="field"><label>New password</label><input type="password" required autoComplete="new-password" value={pwd.value} onChange={(e) => setPwd({ ...pwd, value: e.target.value })} /><small className="muted">8+ characters with upper-case, lower-case and a digit.</small></div>
              <div className="row"><button className="btn btn-primary">Reset</button><button type="button" className="btn" onClick={() => setPwd(null)}>Cancel</button></div>
            </form>
          ) : (
            <form className="card" onSubmit={create}>
              <h2>Add department officer</h2>
              <div className="field"><label>Full name *</label><input required maxLength={100} value={form.name} onChange={set('name')} /></div>
              <div className="field"><label>Email (login) *</label><input type="email" required value={form.email} onChange={set('email')} /></div>
              <div className="field"><label>Phone</label><input maxLength={20} value={form.phone} onChange={set('phone')} /></div>
              <div className="field"><label>Initial password *</label><input type="password" required autoComplete="new-password" value={form.password} onChange={set('password')} /><small className="muted">8+ characters with upper-case, lower-case and a digit.</small></div>
              <div className="field"><label>Department *</label><select required value={form.departmentId} onChange={set('departmentId')}><option value="">Select…</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
              <button className="btn btn-primary btn-block">Create officer</button>
            </form>
          )}
        </div>
      </div>
    </>
  );
}
