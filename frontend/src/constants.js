export const STATUSES = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLUTION_SUBMITTED', 'RESOLVED', 'REJECTED', 'REOPENED'];
export const WORKFLOW = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLUTION_SUBMITTED', 'RESOLVED'];
export const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
export const CATEGORIES = ['ROADS', 'WATER_SUPPLY', 'SANITATION', 'ELECTRICITY', 'STREET_LIGHTS', 'DRAINAGE', 'PUBLIC_SAFETY', 'PARKS', 'PUBLIC_PROPERTY', 'OTHER'];
export const STATUS_COLORS = {
  SUBMITTED: '#64748b', UNDER_REVIEW: '#8b5cf6', ASSIGNED: '#0ea5e9', IN_PROGRESS: '#f59e0b',
  RESOLUTION_SUBMITTED: '#0d9488', RESOLVED: '#16a34a', REJECTED: '#dc2626', REOPENED: '#ea580c',
};
export const label = (s) => (s ? s.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase()) : '');
export const fmtDate = (d) => (d ? new Date(d).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : '—');
export const fmtDay = (d) => (d ? new Date(d).toLocaleDateString([], { dateStyle: 'medium' }) : '—');

/** Human readable SLA text for a complaint ({ slaDueAt, overdue, status }). */
export function slaText(c) {
  if (!c.slaDueAt || c.status === 'RESOLVED' || c.status === 'REJECTED') return null;
  const ms = new Date(c.slaDueAt).getTime() - Date.now();
  const h = Math.abs(ms) / 36e5;
  const txt = h >= 48 ? `${Math.round(h / 24)} d` : `${Math.max(1, Math.round(h))} h`;
  return ms < 0 ? `Overdue by ${txt}` : `${txt} left`;
}
