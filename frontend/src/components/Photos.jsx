/** Thumbnail grid; clicking a photo opens it full size in a new tab. */
export default function Photos({ urls = [], caption }) {
  if (!urls || urls.length === 0) return null;
  return (
    <div className="photos-block">
      {caption && <small className="muted">{caption}</small>}
      <div className="photo-grid">
        {urls.map((u) => (
          <a key={u} href={u} target="_blank" rel="noreferrer"><img src={u} alt="Attached evidence" loading="lazy" /></a>
        ))}
      </div>
    </div>
  );
}
