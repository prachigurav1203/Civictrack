import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('ct_token');
  if (token) cfg.headers.Authorization = `Bearer ${token}`;
  return cfg;
});

api.interceptors.response.use(
  (r) => r,
  (err) => {
    // BUG FIX: a failed LOGIN / REGISTER is also a 401. The old handler hard-redirected on every 401 when an old
    // token was still stored, which reloaded the page and wiped the "Invalid email or password" message.
    const url = err.config?.url || '';
    const isAuthCall = url.includes('/auth/login') || url.includes('/auth/register');
    if (err.response?.status === 401 && !isAuthCall && localStorage.getItem('ct_token')) {
      localStorage.removeItem('ct_token');
      window.location.assign('/login?expired=1');
    }
    return Promise.reject(err);
  }
);

export const errMsg = (e) => {
  if (e?.response?.status === 429 && !e.response.data?.message) return 'Too many requests. Please wait and try again.';
  return e?.response?.data?.message || (e?.code === 'ERR_NETWORK' ? 'Cannot reach the server. Is the backend running on port 8080?' : e?.message) || 'Something went wrong';
};

/** Downloads a binary file (Excel / PDF) through axios so the JWT header is sent. */
export async function download(path, params, fallbackName) {
  try {
    const res = await api.get(path, { params, responseType: 'blob' });
    const disp = res.headers['content-disposition'] || '';
    const m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disp);
    const name = m ? decodeURIComponent(m[1]) : fallbackName;
    const url = URL.createObjectURL(res.data);
    const a = document.createElement('a');
    a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  } catch (e) {
    if (e?.response?.data instanceof Blob) {
      try { const j = JSON.parse(await e.response.data.text()); e.response.data = j; } catch { /* keep original */ }
    }
    throw e;
  }
}

export default api;
