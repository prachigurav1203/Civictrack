import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { errMsg } from '../api/client';
import Alert from '../components/Alert';

export default function Login() {
  const { user, login } = useAuth();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const [f, setF] = useState({ email: '', password: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/dashboard" replace />;

  const submit = async (e) => {
    e.preventDefault(); setErr(''); setBusy(true);
    try { await login(f.email.trim(), f.password); nav('/dashboard'); }
    catch (ex) { setErr(errMsg(ex)); setBusy(false); }
  };

  return (
    <div className="auth-wrap">
      <form className="auth-card" onSubmit={submit}>
        <div className="logo">📍 CivicTrack</div>
        <p className="muted">Sign in to report, manage and track civic issues.</p>
        {params.get('expired') && <Alert type="info">Your session has ended. Please sign in again.</Alert>}
        <Alert>{err}</Alert>
        <div className="field"><label>Email</label><input type="email" required autoComplete="username" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></div>
        <div className="field"><label>Password</label><input type="password" required autoComplete="current-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} /></div>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="mt">New citizen? <Link to="/register">Create an account</Link> · <Link to="/track">Track a complaint</Link></p>
        <p className="muted" style={{ fontSize: '.8rem' }}>Officer accounts are created by the administrator - use the credentials you were given.</p>
      </form>
    </div>
  );
}
