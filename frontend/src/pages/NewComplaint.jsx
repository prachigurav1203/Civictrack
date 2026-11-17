import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api, { errMsg } from '../api/client';
import Alert from '../components/Alert';
import MapPicker from '../components/MapPicker';
import { CATEGORIES, label } from '../constants';

const MAX_PHOTOS = 5;

export default function NewComplaint() {
  const nav = useNavigate();
  const [form, setForm] = useState({ title: '', description: '', category: 'ROADS', address: '' });
  const [pos, setPos] = useState(null);
  const [addrTouched, setAddrTouched] = useState(false);
  const [files, setFiles] = useState([]);
  const [map, setMap] = useState({});
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { api.get('/departments/category-map').then((r) => setMap(Object.fromEntries(r.data.map((m) => [m.category, m.departmentName])))).catch(() => {}); }, []);
  useEffect(() => () => files.forEach((f) => URL.revokeObjectURL(f.preview)), []); // eslint-disable-line

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const onFiles = (e) => {
    setErr('');
    const picked = Array.from(e.target.files);
    e.target.value = '';
    const next = [...files];
    for (const f of picked) {
      if (next.length >= MAX_PHOTOS) { setErr(`You can attach at most ${MAX_PHOTOS} photos.`); break; }
      if (!f.type.startsWith('image/')) { setErr(`"${f.name}" is not an image.`); continue; }
      if (f.size > 5 * 1024 * 1024) { setErr(`"${f.name}" is larger than 5 MB.`); continue; }
      next.push(Object.assign(f, { preview: URL.createObjectURL(f) }));
    }
    setFiles(next);
  };
  const removeFile = (i) => { URL.revokeObjectURL(files[i].preview); setFiles(files.filter((_, x) => x !== i)); };

  const submit = async (e) => {
    e.preventDefault(); setErr('');
    if (!pos) { setErr('Please select the location: search a place, click on the map, drag the marker, or use GPS (GPS is optional).'); return; }
    if (form.description.trim().length < 10) { setErr('Please describe the problem in at least 10 characters.'); return; }
    const fd = new FormData();
    Object.entries(form).forEach(([k, v]) => fd.append(k, v));
    fd.append('latitude', pos.lat);
    fd.append('longitude', pos.lng);
    files.forEach((f) => fd.append('images', f));
    setBusy(true);
    try {
      const { data } = await api.post('/complaints', fd);
      nav(`/complaints/${data.id}`, { state: { created: data.trackingId } });
    } catch (ex) { setErr(errMsg(ex)); setBusy(false); }
  };

  return (
    <>
      <div className="page-head"><div><h1>Report a civic issue</h1><p className="muted">Describe the problem and pin its exact location.</p></div></div>
      <form className="card" onSubmit={submit}>
        <Alert onClose={() => setErr('')}>{err}</Alert>
        <div className="field"><label>Title *</label><input required maxLength={200} value={form.title} onChange={set('title')} placeholder="e.g. Deep pothole near the bus stop" /></div>
        <div className="field"><label>Category *</label>
          <select value={form.category} onChange={set('category')}>{CATEGORIES.map((c) => <option key={c} value={c}>{label(c)}</option>)}</select>
          {map[form.category] && <small className="muted">This will be routed automatically to: <strong>{map[form.category]}</strong></small>}
        </div>
        <div className="field"><label>Detailed description *</label><textarea required minLength={10} maxLength={5000} value={form.description} onChange={set('description')} placeholder="Explain the issue in detail: what, how big, since when…" /></div>
        <div className="field">
          <label>Photos (optional, up to {MAX_PHOTOS}, 5 MB each)</label>
          <input type="file" accept="image/png,image/jpeg,image/gif,image/webp" multiple onChange={onFiles} disabled={files.length >= MAX_PHOTOS} />
          {files.length > 0 && <div className="photo-grid">{files.map((f, i) => (
            <div key={f.preview} className="photo-item"><img src={f.preview} alt="Preview" /><button type="button" onClick={() => removeFile(i)} aria-label="Remove photo">×</button></div>
          ))}</div>}
        </div>
        <div className="field"><label>Location *</label>
          <MapPicker value={pos} onChange={(lat, lng) => setPos({ lat, lng })} onAddress={(a) => { if (!addrTouched) setForm((f) => ({ ...f, address: a.slice(0, 490) })); }} />
        </div>
        <div className="field"><label>Address / landmark (saved with the complaint)</label>
          <input maxLength={500} value={form.address} onChange={(e) => { setAddrTouched(true); set('address')(e); }} placeholder="Filled in automatically from the map; you can edit it" /></div>
        <div className="row"><button className="btn btn-primary" disabled={busy}>{busy ? 'Submitting…' : 'Submit complaint'}</button><button type="button" className="btn" onClick={() => nav('/complaints')}>Cancel</button></div>
      </form>
    </>
  );
}
