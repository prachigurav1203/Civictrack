import { createContext, useContext, useEffect, useState } from 'react';
import api from '../api/client';

const AuthCtx = createContext(null);
export const useAuth = () => useContext(AuthCtx);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!localStorage.getItem('ct_token')) { setLoading(false); return; }
    api.get('/auth/me')
      .then((r) => setUser(r.data))
      .catch(() => localStorage.removeItem('ct_token'))
      .finally(() => setLoading(false));
  }, []);

  const authenticate = async (path, body) => {
    const { data } = await api.post(path, body);
    localStorage.setItem('ct_token', data.token);
    setUser(data.user);
    return data.user;
  };

  const value = {
    user, loading,
    login: (email, password) => authenticate('/auth/login', { email, password }),
    register: (body) => authenticate('/auth/register', body),
    // Logout revokes the token on the server too, then clears the local copy.
    logout: async () => {
      try { await api.post('/auth/logout'); } catch { /* token may already be invalid */ }
      localStorage.removeItem('ct_token');
      setUser(null);
    },
  };
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}
