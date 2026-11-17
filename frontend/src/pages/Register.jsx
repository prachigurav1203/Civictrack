import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { errMsg } from '../api/client';
import Alert from '../components/Alert';

const strong = (p) => p.length >= 8 && /[a-z]/.test(p) && /[A-Z]/.test(p) && /\d/.test(p);

export default function Register() {
  const { user, register } = useAuth();
  const nav = useNavigate();
  const [f, setF] = useState({ name: '', email: '', phone: '', password: '', confirm: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/dashboard" replace />;
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault(); setErr('');
    if (!strong(f.password)) { setErr('Password must be at least 8 characters and include an upper-case letter, a lower-case letter and a digit.'); return; }
    if (f.password !== f.confirm) { setErr('The two passwords do not match.'); return; }
    setBusy(true);
    try { await register({ name: f.name.trim(), email: f.email.trim(), phone: f.phone.trim(), password: f.password }); nav('/dashboard'); }
    catch (ex) { setErr(errMsg(ex)); setBusy(false); }
  };

  return (
    <div className="auth-wrap">
      <form className="auth-card" onSubmit={submit}>
        <div className="logo">📍 CivicTrack</div>
        <p className="muted">Create a citizen account to report civic issues.</p>
        <Alert>{err}</Alert>
        <div className="field"><label>Full name</label><input required maxLength={100} value={f.name} onChange={set('name')} /></div>
        <div className="field"><label>Email</label><input type="email" required maxLength={150} autoComplete="username" value={f.email} onChange={set('email')} /></div>
        <div className="field"><label>Phone (optional)</label><input value={f.phone} onChange={set('phone')} maxLength={20} /></div>
        <div className="field"><label>Password</label><input type="password" required autoComplete="new-password" value={f.password} onChange={set('password')} />
          <small className="muted">8+ characters with upper-case, lower-case and a digit.</small></div>
        <div className="field"><label>Confirm password</label><input type="password" required autoComplete="new-password" value={f.confirm} onChange={set('confirm')} /></div>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creating…' : 'Register'}</button>
        <p className="mt">Already registered? <Link to="/login">Sign in</Link></p>
      </form>
    </div>
  );
}
