import { useEffect, useState } from 'react';
import api, { errMsg } from '../api/client';
import Alert from '../components/Alert';
import { label } from '../constants';

const EMPTY = { name: '', description: '' };

export default function Departments() {
  const [list, setList] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [editId, setEditId] = useState(null);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');

  const [maps, setMaps] = useState([]);
  const load = () => api.get('/departments').then((r) => setList(r.data)).catch((e) => setErr(errMsg(e)));
  const loadMaps = () => api.get('/admin/category-departments').then((r) => setMaps(r.data)).catch((e) => setErr(errMsg(e)));
  useEffect(() => { load(); loadMaps(); }, []);
  const changeMap = async (category, departmentId) => {
    setErr(''); setMsg('');
    try { await api.put(`/admin/category-departments/${category}`, { departmentId: Number(departmentId) }); setMsg(`${label(category)} will now be routed to the selected department.`); loadMaps(); }
    catch (ex) { setErr(errMsg(ex)); }
  };

  const submit = async (e) => {
    e.preventDefault(); setErr(''); setMsg('');
    try {
      if (editId) await api.put(`/departments/${editId}`, form); else await api.post('/departments', form);
      setMsg(editId ? 'Department updated.' : 'Department created.');
      setForm(EMPTY); setEditId(null); load();
    } catch (ex) { setErr(errMsg(ex)); }
  };

  const remove = async (d) => {
    if (!window.confirm(`Delete "${d.name}"?`)) return;
    setErr(''); setMsg('');
    try { await api.delete(`/departments/${d.id}`); setMsg('Department deleted.'); load(); } catch (ex) { setErr(errMsg(ex)); }
  };

  return (
    <>
      <div className="page-head"><div><h1>Departments</h1><p className="muted">Manage departments that handle complaints</p></div></div>
      <Alert onClose={() => setErr('')}>{err}</Alert>
      <Alert type="success" onClose={() => setMsg('')}>{msg}</Alert>
      <div className="grid grid-detail">
        <div className="card table-wrap">
          <table>
            <thead><tr><th>Name</th><th>Description</th><th /></tr></thead>
            <tbody>{list.map((d) => (
              <tr key={d.id}><td><strong>{d.name}</strong></td><td className="wrap">{d.description}</td>
                <td className="row" style={{ flexWrap: 'nowrap' }}>
                  <button className="btn btn-sm" onClick={() => { setEditId(d.id); setForm({ name: d.name, description: d.description || '' }); }}>Edit</button>
                  <button className="btn btn-sm btn-danger" onClick={() => remove(d)}>Delete</button></td></tr>
            ))}</tbody>
          </table>
          {list.length === 0 && <div className="empty">No departments yet.</div>}
        </div>
        <form className="card" onSubmit={submit}>
          <h2>{editId ? 'Edit department' : 'Add department'}</h2>
          <div className="field"><label>Name *</label><input required maxLength={100} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></div>
          <div className="field"><label>Description</label><textarea style={{ minHeight: 80 }} maxLength={500} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></div>
          <div className="row"><button className="btn btn-primary">{editId ? 'Save' : 'Add'}</button>
            {editId && <button type="button" className="btn" onClick={() => { setEditId(null); setForm(EMPTY); }}>Cancel</button>}</div>
        </form>
      </div>
      <div className="card mt">
        <h2>Automatic department assignment</h2>
        <p className="muted" style={{ marginTop: 0 }}>New complaints are routed to the department mapped to their category. Admins can still change the department per complaint.</p>
        <div className="table-wrap"><table>
          <thead><tr><th>Category</th><th>Handled by</th></tr></thead>
          <tbody>{maps.map((m) => (
            <tr key={m.category}><td><strong>{label(m.category)}</strong></td>
              <td><select value={m.departmentId || ''} onChange={(e) => changeMap(m.category, e.target.value)}><option value="" disabled>Not mapped</option>{list.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></td></tr>
          ))}</tbody>
        </table></div>
      </div>
    </>
  );
}
