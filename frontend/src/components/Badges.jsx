import { label, slaText } from '../constants';

export const StatusBadge = ({ status }) => <span className={`badge st-${status}`}>{label(status)}</span>;
export const PriorityBadge = ({ priority }) => <span className={`badge pr-${priority}`}>{label(priority)}</span>;

export function SlaBadge({ c }) {
  const t = slaText(c);
  if (!t) return <span className="muted">—</span>;
  return <span className={`badge ${c.overdue ? 'sla-late' : 'sla-ok'}`} title={`SLA deadline: ${new Date(c.slaDueAt).toLocaleString()}`}>{t}</span>;
}
