import { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import { Link } from 'react-router-dom';
import L, { DEFAULT_CENTER, OSM_URL, OSM_ATTR } from './leafletSetup';

function Fit({ markers }) {
  const map = useMap();
  const key = markers.map((m) => `${m.lat},${m.lng}`).join('|');
  useEffect(() => {
    if (!markers.length) return;
    if (markers.length === 1) map.setView([markers[0].lat, markers[0].lng], 16);
    else map.fitBounds(L.latLngBounds(markers.map((m) => [m.lat, m.lng])), { padding: [30, 30], maxZoom: 16 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  return null;
}

/** Read-only map. markers: [{ lat, lng, title, subtitle, link }] */
export default function MapView({ markers = [], height = 300 }) {
  const first = markers[0] ? [markers[0].lat, markers[0].lng] : DEFAULT_CENTER;
  return (
    <MapContainer center={first} zoom={markers.length ? 14 : 5} style={{ height, width: '100%' }} className="map-box">
      <TileLayer url={OSM_URL} attribution={OSM_ATTR} />
      <Fit markers={markers} />
      {markers.map((m, i) => (
        <Marker key={i} position={[m.lat, m.lng]}>
          {(m.title || m.link) && (
            <Popup>
              <strong>{m.title}</strong>
              {m.subtitle && <div>{m.subtitle}</div>}
              {m.link && <Link to={m.link}>View details</Link>}
            </Popup>
          )}
        </Marker>
      ))}
    </MapContainer>
  );
}
