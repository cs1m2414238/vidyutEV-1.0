import React from 'react';
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
}

// Custom Green & Busy marker icons using SVG data URI
const createCustomIcon = (isAvailable: boolean) => {
  const color = isAvailable ? '#00A86B' : '#F97316';
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

const createClusterIcon = (count: number) => {
  const html = `<div style="background:#10B981;color:#fff;font-weight:800;font-size:12px;width:28px;height:28px;border-radius:50%;display:flex;align-items:center;justify-content:center;border:2px solid #ffffff;box-shadow:0 2px 6px rgba(0,0,0,0.2);">${count}</div>`;
  return L.divIcon({
    className: 'custom-cluster-pin',
    html: html,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
};

export const MapView: React.FC<MapViewProps> = ({
  chargers,
  onSelect,
  filter,
  onFilterChange,
}) => {
  const lucknowPos: [number, number] = [26.8467, 80.9462];

  const filteredChargers = chargers.filter((c) => {
    if (filter === 'available') return c.available;
    if (filter === 'fast') return c.powerKw >= 11;
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

        <button style={styles.filterBtn}>
          🎛️ More Filters
        </button>
      </div>

      {/* Interactive Map */}
      <div style={styles.mapContainer}>
        <MapContainer
          center={lucknowPos}
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
              icon={createCustomIcon(c.available)}
              eventHandlers={{
                click: () => onSelect(c),
              }}
            >
              <Popup>
                <div style={styles.popup}>
                  <div style={styles.popupTitle}>{c.name}</div>
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

          {/* Cluster Circle Markers matching prototype */}
          <Marker position={[26.865, 80.935]} icon={createClusterIcon(3)} />
          <Marker position={[26.835, 80.965]} icon={createClusterIcon(3)} />
          <Marker position={[26.820, 80.915]} icon={createClusterIcon(5)} />
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
  popup: {
    padding: 4,
    minWidth: 160,
  },
  popupTitle: {
    fontSize: 14,
    fontWeight: 800,
    color: '#1E293B',
  },
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
