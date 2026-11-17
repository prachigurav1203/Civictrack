import { StatusBadge } from './Badges';
import { fmtDate, label } from '../constants';

export default function Timeline({ history = [] }) {
  if (!history.length) return <p className="muted">No history yet.</p>;
  return (
    <ul className="timeline">
      {history.map((h, i) => (
        <li key={i}>
          <span className="dot" />
          <div>
            <StatusBadge status={h.status} />
            {h.remarks && <p className="tl-remarks">{h.remarks}</p>}
            <small className="muted">{fmtDate(h.changedAt)}{h.changedBy ? ` · ${h.changedBy}${h.changedByRole ? ` (${label(h.changedByRole)})` : ''}` : ''}</small>
          </div>
        </li>
      ))}
    </ul>
  );
}
