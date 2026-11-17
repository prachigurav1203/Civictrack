import { useEffect, useRef, useState } from 'react';
import { MapContainer, TileLayer, Marker, useMap, useMapEvents } from 'react-leaflet';
import api, { errMsg } from '../api/client';
import { DEFAULT_CENTER, OSM_URL, OSM_ATTR } from './leafletSetup';

function ClickHandler({ onPick }) {
  useMapEvents({ click: (e) => onPick(e.latlng.lat, e.latlng.lng) });
  return null;
}

function Recenter({ value }) {
  const map = useMap();
  useEffect(() => {
    if (value) map.setView([value.lat, value.lng], Math.max(map.getZoom(), 16));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value?.lat, value?.lng]);
  return null;
}

const round = (n) => Math.round(n * 1e6) / 1e6;

/**
 * Location picker. GPS is OPTIONAL - the citizen can also:
 *  - search a place / area / landmark / address (autocomplete suggestions via the backend geocoding proxy),
 *  - click on the map, or drag the marker.
 * value: { lat, lng } | null,  onChange(lat, lng),  onAddress(text)  (readable address for the chosen point)
 */
export default function MapPicker({ value, onChange, onAddress, height = 340 }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const [msg, setMsg] = useState('');
  const [gpsBusy, setGpsBusy] = useState(false);
  const [address, setAddress] = useState('');
  const skipReverse = useRef(false);
  const lastPicked = useRef('');

  const runSearch = async (q) => {
    setSearching(true); setMsg('');
    try {
      const { data } = await api.get('/geo/search', { params: { q } });
      setResults(data); setOpen(true);
      if (!data.length) setMsg('No matching place found. Try another spelling, or click on the map.');
    } catch (e) { setResults([]); setMsg(errMsg(e)); }
    setSearching(false);
  };

  // autocomplete suggestions: wait until the user stops typing (keeps us within the geocoder usage limits)
  useEffect(() => {
    const q = query.trim();
    if (q.length < 3 || q === lastPicked.current) { setResults([]); return undefined; }
    const t = setTimeout(() => runSearch(q), 800);
    return () => clearTimeout(t);
  }, [query]);

  // readable address for the selected point (skipped when it already came from a search result)
  useEffect(() => {
    if (!value) return undefined;
    if (skipReverse.current) { skipReverse.current = false; return undefined; }
    let ignore = false;
    const t = setTimeout(async () => {
      try {
        const { data } = await api.get('/geo/reverse', { params: { lat: value.lat, lon: value.lng } });
        if (!ignore) { setAddress(data.displayName); onAddress?.(data.displayName); }
      } catch { if (!ignore) setMsg('Could not look up the address for this point. You can type the address yourself below.'); }
    }, 900);
    return () => { ignore = true; clearTimeout(t); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value?.lat, value?.lng]);

  const pick = (p) => {
    skipReverse.current = true;
    lastPicked.current = p.displayName;
    setQuery(p.displayName); setOpen(false); setResults([]); setMsg('');
    setAddress(p.displayName); onAddress?.(p.displayName);
    onChange(round(p.lat), round(p.lon));
  };

  const choose = (lat, lng) => { setMsg(''); onChange(round(lat), round(lng)); };

  const useGps = () => {
    if (!navigator.geolocation) { setMsg('This browser does not support GPS. Search a place or click on the map instead.'); return; }
    setGpsBusy(true); setMsg('Getting your location…');
    navigator.geolocation.getCurrentPosition(
      (p) => { choose(p.coords.latitude, p.coords.longitude); setMsg(`GPS location captured (±${Math.round(p.coords.accuracy)} m)`); setGpsBusy(false); },
      (err) => {
        setMsg(err.code === 1 ? 'Location permission was denied - no problem: search a place above or click on the map.' : 'Could not get your GPS location. Search a place or click on the map.');
        setGpsBusy(false);
      },
      { enableHighAccuracy: true, timeout: 12000 }
    );
  };

  return (
    <div>
      <div className="map-tools">
        <div className="geo-search">
          <input value={query} placeholder="Search a place, area, landmark or address…"
            onChange={(e) => setQuery(e.target.value)}
            onFocus={() => results.length && setOpen(true)}
            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); if (query.trim().length >= 3) runSearch(query.trim()); } if (e.key === 'Escape') setOpen(false); }} />
          {searching && <span className="geo-spin" aria-label="Searching" />}
          {open && results.length > 0 && (
            <ul className="geo-list">
              {results.map((p, i) => (
                <li key={`${p.lat},${p.lon},${i}`}><button type="button" onClick={() => pick(p)}>📍 {p.displayName}</button></li>
              ))}
            </ul>
          )}
        </div>
        <button type="button" className="btn btn-sm" onClick={useGps} disabled={gpsBusy}>🎯 Use my GPS (optional)</button>
      </div>
      <MapContainer center={value ? [value.lat, value.lng] : DEFAULT_CENTER} zoom={value ? 16 : 5} style={{ height, width: '100%' }} className="map-box">
        <TileLayer url={OSM_URL} attribution={OSM_ATTR} />
        <ClickHandler onPick={choose} />
        <Recenter value={value} />
        {value && (
          <Marker position={[value.lat, value.lng]} draggable
            eventHandlers={{ dragend: (e) => { const p = e.target.getLatLng(); choose(p.lat, p.lng); } }} />
        )}
      </MapContainer>
      <div className="geo-selected">
        {value ? (
          <>
            <strong>Selected location:</strong> {value.lat.toFixed(6)}, {value.lng.toFixed(6)}
            {address && <div className="muted">📍 {address}</div>}
            <small className="muted">Drag the marker or click elsewhere on the map to adjust.</small>
          </>
        ) : <span className="muted">No location selected yet - search above, use GPS, or click on the map.</span>}
        {msg && <div className="geo-msg">{msg}</div>}
      </div>
    </div>
  );
}
