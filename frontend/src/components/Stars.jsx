import { useState } from 'react';

/** Read-only (value) or interactive (onChange) 1-5 star rating. */
export default function Stars({ value = 0, onChange, size = '1.4rem' }) {
  const [hover, setHover] = useState(0);
  const shown = hover || value;
  return (
    <span className="stars" style={{ fontSize: size }} role={onChange ? 'radiogroup' : 'img'} aria-label={`${value} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={n <= shown ? 'on' : ''}
          style={{ cursor: onChange ? 'pointer' : 'default' }}
          onMouseEnter={() => onChange && setHover(n)} onMouseLeave={() => onChange && setHover(0)}
          onClick={() => onChange && onChange(n)}>★</span>
      ))}
    </span>
  );
}
