export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  const start = Math.max(0, Math.min(page - 2, totalPages - 5));
  const pages = Array.from({ length: Math.min(5, totalPages) }, (_, i) => start + i);
  return (
    <div className="pagination">
      <button className="btn btn-sm" disabled={page === 0} onClick={() => onChange(page - 1)}>‹ Prev</button>
      {pages.map((p) => (
        <button key={p} className={`btn btn-sm ${p === page ? 'btn-primary' : ''}`} onClick={() => onChange(p)}>{p + 1}</button>
      ))}
      <button className="btn btn-sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>Next ›</button>
    </div>
  );
}
