import { WORKFLOW, label } from '../constants';

/** Visual progress tracker for the complaint workflow. */
export default function StatusStepper({ status }) {
  if (status === 'REJECTED') return <div className="alert alert-error" style={{ marginBottom: 0 }}>This complaint was rejected. See the history for the reason.</div>;
  const idx = status === 'REOPENED' ? 1 : WORKFLOW.indexOf(status);
  return (
    <>
      <ol className="stepper">
        {WORKFLOW.map((s, i) => (
          <li key={s} className={i < idx ? 'done' : i === idx ? 'current' : ''}>
            <span className="step-dot">{i < idx ? '✓' : i + 1}</span>
            <span className="step-label">{label(s)}</span>
          </li>
        ))}
      </ol>
      {status === 'REOPENED' && <p className="muted" style={{ margin: '.5rem 0 0' }}>Reopened - waiting for the admin to assign an officer again.</p>}
    </>
  );
}
