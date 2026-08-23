import React, { useMemo, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { Charger } from '../types';

interface MapViewProps {
  chargers: Charger[];
  selectedId: number | null;
  onSelect: (charger: Charger) => void;
  filter: 'all' | 'available' | 'fast';
  onFilterChange: (filter: 'all' | 'available' | 'fast') => void;
  searchQuery?: string;
  center?: [number, number];
  compatibleConnector?: string;
  offline?: boolean;
}

// Custom Green & Busy marker icons using SVG data URI
const createCustomIcon = (charger: Charger) => {
  const unavailable = charger.status === 'OFFLINE' || charger.status === 'MAINTENANCE' || charger.availability === 'UNAVAILABLE';
  const color = unavailable ? '#94A3B8' : charger.available ? '#00A86B' : '#F97316';
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 46" width="36" height="46">
      <path d="M18 0C8.06 0 0 8.06 0 18c0 13.5 18 28 18 28s18-14.5 18-28C36 8.06 27.94 0 18 0z" fill="${color}"/>
      <circle cx="18" cy="17" r="13" fill="#ffffff"/>
      <path d="M17 9l-5 9h5v7l6-9h-6V9z" fill="${color}"/>
    </svg>
  `;

  return L.divIcon({
    className: 'custom-leaflet-pin',
    html: svg,
    iconSize: [36, 46],
    iconAnchor: [18, 46],
    popupAnchor: [0, -42],
  });
};

const normalizeConnector = (value?: string) => (value || '').toUpperCase().replace(/[^A-Z0-9]/g, '');

export const MapView: React.FC<MapViewProps> = ({
  chargers,
  onSelect,
  filter,
  onFilterChange,
  searchQuery = '',
  center = [26.8467, 80.9462],
  compatibleConnector,
  offline = false,
}) => {
  const [showMore, setShowMore] = useState(false);
  const [connector, setConnector] = useState('COMPATIBLE');
  const [maxPrice, setMaxPrice] = useState(50);
  const [minimumPower, setMinimumPower] = useState(0);
  const normalizedQuery = searchQuery.trim().toLocaleLowerCase();
  const connectorOptions = useMemo(() => Array.from(new Set(chargers.map((charger) => charger.connectorType).filter(Boolean))).sort(), [chargers]);

  const filteredChargers = chargers.filter((c) => {
    if (normalizedQuery && !`${c.name} ${c.address}`.toLocaleLowerCase().includes(normalizedQuery)) return false;
    if (filter === 'available' && !c.available) return false;
    if (filter === 'fast' && c.powerKw < 11) return false;
    if (c.pricePerKwh > maxPrice || c.powerKw < minimumPower) return false;
    const requiredConnector = connector === 'COMPATIBLE' ? compatibleConnector : connector === 'ALL' ? '' : connector;
    if (requiredConnector && normalizeConnector(c.connectorType) !== normalizeConnector(requiredConnector)) return false;
    return true;
  });

  return (
    <div style={styles.container}>
      {/* Map Filter Controls Bar */}
      <div style={styles.filterBar}>
        <button
          style={{
            ...styles.filterBtn,
            ...(filter === 'all' ? styles.filterBtnActive : {}),
          }}
          onClick={() => onFilterChange('all')}
        >
          ⚡ All Chargers
        </button>

        <button
          style={{
            ...styles.filterBtn,
            ...(filter === 'available' ? styles.filterBtnActive : {}),
          }}
          onClick={() => onFilterChange('available')}
        >
          🟢 Available
        </button>

        <button
          style={{
            ...styles.filterBtn,
            ...(filter === 'fast' ? styles.filterBtnActive : {}),
          }}
          onClick={() => onFilterChange('fast')}
        >
          🚀 Fast Charger (≥11kW)
        </button>

        <button style={{ ...styles.filterBtn, ...(showMore ? styles.filterBtnActive : {}) }} onClick={() => setShowMore((value) => !value)}>
          🎛️ More Filters
        </button>
      </div>

      {showMore && <div style={styles.moreFilters}>
        <label style={styles.filterLabel}>Connector<select style={styles.select} value={connector} onChange={(event) => setConnector(event.target.value)}><option value="COMPATIBLE">Compatible with {compatibleConnector || 'my vehicle'}</option><option value="ALL">All connectors</option>{connectorOptions.map((item) => <option key={item}>{item}</option>)}</select></label>
        <label style={styles.filterLabel}>Maximum ₹/kWh<input type="number" min="0" max="100" style={styles.input} value={maxPrice} onChange={(event) => setMaxPrice(Number(event.target.value))} /></label>
        <label style={styles.filterLabel}>Minimum speed<select style={styles.select} value={minimumPower} onChange={(event) => setMinimumPower(Number(event.target.value))}><option value={0}>Any speed</option><option value={7}>7+ kW</option><option value={30}>30+ kW</option><option value={60}>60+ kW</option><option value={120}>120+ kW</option></select></label>
        <span style={styles.resultCount}>{filteredChargers.length} matching stations</span>
      </div>}
      {offline && <div style={styles.offlineBanner}>Offline — showing the last known charger network. Booking will retry when the connection returns.</div>}

      {/* Interactive Map */}
      <div style={styles.mapContainer}>
        <MapContainer
          key={`${center[0]}-${center[1]}`}
          center={center}
          zoom={13}
          scrollWheelZoom={true}
          style={{ height: '100%', width: '100%', borderRadius: 16 }}
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />

          {/* Station Markers */}
          {filteredChargers.map((c) => (
            <Marker
              key={c.id}
              position={[c.latitude, c.longitude]}
              icon={createCustomIcon(c)}
              eventHandlers={{
                click: () => onSelect(c),
              }}
            >
              <Popup>
                <div style={styles.popup}>
                  <div style={styles.popupTitle}>{c.name}</div>
                  {c.outletPartner && <div style={styles.outletBadge}>{c.outletInstitutionName || 'Outlet partner'}</div>}
                  <div style={styles.popupAddress}>{c.address}</div>
                  <div style={styles.popupMeta}>
                    <span>{c.powerKw} kW</span> • <span>{c.connectorType}</span>
                  </div>
                  <div style={styles.popupPrice}>₹{c.pricePerKwh} / kWh</div>
                  <button
                    style={styles.popupBtn}
                    onClick={() => onSelect(c)}
                  >
                    Book Charger Now
                  </button>
                </div>
              </Popup>
            </Marker>
          ))}

        </MapContainer>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
  },
  filterBar: {
    display: 'flex',
    gap: 8,
  },
  filterBtn: {
    padding: '8px 14px',
    borderRadius: 10,
    backgroundColor: '#fff',
    border: '1px solid #E2E8F0',
    fontSize: 12,
    fontWeight: 600,
    color: '#64748B',
    cursor: 'pointer',
    transition: 'all 0.15s',
  },
  filterBtnActive: {
    backgroundColor: '#00A86B',
    color: '#fff',
    borderColor: '#00A86B',
  },
  mapContainer: {
    height: 440,
    borderRadius: 16,
    overflow: 'hidden',
    border: '1px solid #E2E8F0',
    boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
  },
  moreFilters: { display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'end', padding: 12, border: '1px solid #DDE8E3', borderRadius: 12, background: '#fff' },
  filterLabel: { display: 'grid', gap: 4, color: '#475467', fontSize: 10, fontWeight: 700 },
  select: { minWidth: 150, padding: '8px 9px', border: '1px solid #D8E2DE', borderRadius: 8, color: '#344054', background: '#fff', fontSize: 11 },
  input: { width: 105, padding: '8px 9px', border: '1px solid #D8E2DE', borderRadius: 8, color: '#344054', fontSize: 11 },
  resultCount: { marginLeft: 'auto', padding: '8px 10px', borderRadius: 999, color: '#087454', background: '#EAF8F2', fontSize: 10, fontWeight: 800 },
  offlineBanner: { padding: '9px 12px', border: '1px solid #F3D19C', borderRadius: 10, color: '#9A5B13', background: '#FFF9EB', fontSize: 10, fontWeight: 650 },
  popup: {
    padding: 4,
    minWidth: 160,
  },
  popupTitle: {
    fontSize: 14,
    fontWeight: 800,
    color: '#1E293B',
  },
  outletBadge: { display: 'inline-block', margin: '3px 0', padding: '2px 6px', borderRadius: 999, color: '#6D28D9', background: '#F0E9FF', fontSize: 9, fontWeight: 800 },
  popupAddress: {
    fontSize: 11,
    color: '#64748B',
    margin: '2px 0 6px',
  },
  popupMeta: {
    fontSize: 11,
    fontWeight: 600,
    color: '#00A86B',
  },
  popupPrice: {
    fontSize: 13,
    fontWeight: 800,
    color: '#1E293B',
    marginTop: 4,
  },
  popupBtn: {
    marginTop: 8,
    width: '100%',
    backgroundColor: '#00A86B',
    color: '#fff',
    border: 'none',
    borderRadius: 6,
    padding: '6px',
    fontSize: 11,
    fontWeight: 700,
    cursor: 'pointer',
  },
};
