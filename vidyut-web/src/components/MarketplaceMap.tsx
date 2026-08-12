import { useEffect, useMemo, useState } from 'react';
import L from 'leaflet';
import { CircleMarker, MapContainer, Marker, Popup, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import { Crosshair, LocateFixed, MapPin, Search } from 'lucide-react';
import type { MarketplaceStation, PropertyOpportunity } from '../services/marketplace';
import 'leaflet/dist/leaflet.css';

export interface PropertyMapSelection {
  latitude: number;
  longitude: number;
  address?: string;
  city?: string;
  state?: string;
  pincode?: string;
}

interface GeocodeAddress {
  road?: string;
  neighbourhood?: string;
  suburb?: string;
  city?: string;
  town?: string;
  village?: string;
  municipality?: string;
  county?: string;
  state?: string;
  postcode?: string;
}

interface GeocodeResult {
  lat: string;
  lon: string;
  display_name?: string;
  address?: GeocodeAddress;
}

const propertyPin = L.divIcon({
  className: 'marketplace-map-pin-shell',
  html: '<span class="marketplace-map-pin"><span></span></span>',
  iconSize: [34, 42],
  iconAnchor: [17, 42],
  popupAnchor: [0, -38],
});

const addressFields = (result: GeocodeResult): Omit<PropertyMapSelection, 'latitude' | 'longitude'> => {
  const address = result.address ?? {};
  const city = address.city ?? address.town ?? address.village ?? address.municipality ?? address.county;
  return {
    address: result.display_name,
    city,
    state: address.state,
    pincode: address.postcode,
  };
};

const locationSearchUrl = (query: string) =>
  `https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&countrycodes=in&limit=1&q=${encodeURIComponent(query)}`;

const reverseLookupUrl = (latitude: number, longitude: number) =>
  `https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&zoom=18&lat=${latitude}&lon=${longitude}`;

export function PropertyLocationPicker({
  latitude,
  longitude,
  address,
  onChange,
}: {
  latitude: number;
  longitude: number;
  address?: string;
  onChange: (selection: PropertyMapSelection) => void;
}) {
  const [query, setQuery] = useState(address ?? '');
  const [lookupState, setLookupState] = useState<'idle' | 'searching' | 'located'>('idle');
  const [message, setMessage] = useState('Search an area or click the map to place the land pin.');
  const validLatitude = Number.isFinite(latitude) ? latitude : 26.8467;
  const validLongitude = Number.isFinite(longitude) ? longitude : 80.9462;

  useEffect(() => {
    if (address && !query) setQuery(address);
  }, [address, query]);

  const selectPoint = async (nextLatitude: number, nextLongitude: number, lookupAddress = true) => {
    const coordinates = {
      latitude: Number(nextLatitude.toFixed(6)),
      longitude: Number(nextLongitude.toFixed(6)),
    };
    onChange(coordinates);
    setLookupState('searching');
    setMessage('Pin placed. Looking up the mapped address…');
    if (!lookupAddress) return;
    try {
      const response = await fetch(reverseLookupUrl(coordinates.latitude, coordinates.longitude), {
        headers: { Accept: 'application/json', 'Accept-Language': 'en-IN,en' },
      });
      if (!response.ok) throw new Error('Address lookup unavailable');
      const result = await response.json() as GeocodeResult;
      const resolved = { ...coordinates, ...addressFields(result) };
      onChange(resolved);
      if (result.display_name) setQuery(result.display_name);
      setLookupState('located');
      setMessage('Mapped address found. Confirm the land details below before publishing.');
    } catch {
      setLookupState('located');
      setMessage('Pin saved. Address lookup was unavailable, so enter the address fields manually.');
    }
  };

  const searchLocation = async () => {
    const value = query.trim();
    if (!value) {
      setMessage('Enter an area, landmark, road, or PIN code first.');
      return;
    }
    setLookupState('searching');
    setMessage('Searching the map…');
    try {
      const response = await fetch(locationSearchUrl(value), {
        headers: { Accept: 'application/json', 'Accept-Language': 'en-IN,en' },
      });
      if (!response.ok) throw new Error('Location search unavailable');
      const results = await response.json() as GeocodeResult[];
      const result = results[0];
      if (!result) {
        setLookupState('idle');
        setMessage('No matching area found. Try a nearby landmark or place the pin manually.');
        return;
      }
      const nextLatitude = Number(result.lat);
      const nextLongitude = Number(result.lon);
      onChange({ latitude: nextLatitude, longitude: nextLongitude, ...addressFields(result) });
      if (result.display_name) setQuery(result.display_name);
      setLookupState('located');
      setMessage('Area found. Drag or click to place the pin precisely on the land parcel.');
    } catch {
      setLookupState('idle');
      setMessage('Map search is temporarily unavailable. You can still place the pin manually.');
    }
  };

  const useCurrentLocation = () => {
    if (!navigator.geolocation) {
      setMessage('Current location is not supported by this browser.');
      return;
    }
    setLookupState('searching');
    setMessage('Finding your current location…');
    navigator.geolocation.getCurrentPosition(
      position => void selectPoint(position.coords.latitude, position.coords.longitude),
      () => {
        setLookupState('idle');
        setMessage('Location permission was unavailable. Search or place the pin manually.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  return <section className="property-location-picker wide" aria-label="Land location map">
    <div className="marketplace-map-heading">
      <div><span><MapPin size={17} /></span><div><strong>Pin the land location</strong><small>Coordinates are captured automatically from the selected point.</small></div></div>
      <button type="button" onClick={useCurrentLocation} disabled={lookupState === 'searching'}><LocateFixed size={15} /> Use my location</button>
    </div>
    <div className="marketplace-location-search">
      <Search size={16} />
      <input value={query} onChange={event => setQuery(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); void searchLocation(); } }} placeholder="Search area, landmark, road or PIN code" />
      <button type="button" onClick={() => void searchLocation()} disabled={lookupState === 'searching'}>{lookupState === 'searching' ? 'Finding…' : 'Find area'}</button>
    </div>
    <div className="property-location-map">
      <MapContainer center={[validLatitude, validLongitude]} zoom={14} scrollWheelZoom className="marketplace-leaflet-map">
        <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        <MapPositionSync latitude={validLatitude} longitude={validLongitude} />
        <MapClickPicker onSelect={(lat, lng) => void selectPoint(lat, lng)} />
        <Marker
          position={[validLatitude, validLongitude]}
          icon={propertyPin}
          draggable
          eventHandlers={{ dragend: event => { const point = (event.target as L.Marker).getLatLng(); void selectPoint(point.lat, point.lng); } }}
        >
          <Popup>Selected land location<br />{validLatitude.toFixed(6)}, {validLongitude.toFixed(6)}</Popup>
        </Marker>
      </MapContainer>
      <div className="map-pin-instruction"><Crosshair size={14} /> Click or drag the pin for parcel-level accuracy</div>
    </div>
    <div className="map-selection-status" role="status"><span>{validLatitude.toFixed(6)}, {validLongitude.toFixed(6)}</span><p>{message}</p></div>
  </section>;
}

function MapClickPicker({ onSelect }: { onSelect: (latitude: number, longitude: number) => void }) {
  useMapEvents({ click: event => onSelect(event.latlng.lat, event.latlng.lng) });
  return null;
}

function MapPositionSync({ latitude, longitude }: { latitude: number; longitude: number }) {
  const map = useMap();
  useEffect(() => {
    map.setView([latitude, longitude], Math.max(map.getZoom(), 14), { animate: true });
  }, [latitude, longitude, map]);
  return null;
}

const distanceKm = (first: { latitude: number; longitude: number }, second: { latitude: number; longitude: number }) => {
  const radians = (degrees: number) => degrees * Math.PI / 180;
  const latitudeDelta = radians(second.latitude - first.latitude);
  const longitudeDelta = radians(second.longitude - first.longitude);
  const a = Math.sin(latitudeDelta / 2) ** 2
    + Math.cos(radians(first.latitude)) * Math.cos(radians(second.latitude)) * Math.sin(longitudeDelta / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
};

const densityTone = (count: number) => count === 0
  ? { label: 'Underserved', color: '#07865d' }
  : count <= 2
    ? { label: 'Low density', color: '#d68a05' }
    : { label: 'Higher density', color: '#dc4c3f' };

export function ChargerDensityMap({
  opportunities,
  stations,
  onContact,
}: {
  opportunities: PropertyOpportunity[];
  stations: MarketplaceStation[];
  onContact: (property: PropertyOpportunity) => void;
}) {
  const [radiusKm, setRadiusKm] = useState(5);
  const activeStations = useMemo(() => stations.filter(station => station.status === 'ACTIVE'), [stations]);
  const density = useMemo(() => opportunities.map(property => ({
    property,
    stationCount: activeStations.filter(station => distanceKm(property, station) <= radiusKm).length,
  })), [activeStations, opportunities, radiusKm]);
  const underserved = density.filter(item => item.stationCount === 0).length;
  const points = [...opportunities.map(item => [item.latitude, item.longitude] as [number, number]), ...activeStations.map(item => [item.latitude, item.longitude] as [number, number])];
  const center = points[0] ?? [26.8467, 80.9462];

  return <section className="charger-density-panel">
    <div className="density-map-head">
      <div><span><MapPin size={20} /></span><div><small>NETWORK PLANNING MAP</small><h2>Find low-density charger areas</h2><p>Green Host sites have no active charger within the selected radius.</p></div></div>
      <aside><strong>{underserved}</strong><span>underserved sites</span></aside>
    </div>
    <div className="density-map-toolbar">
      <div className="density-legend"><span className="underserved">Underserved</span><span className="low">Low density</span><span className="dense">Higher density</span><span className="station">Existing charger</span></div>
      <label>Density radius<select value={radiusKm} onChange={event => setRadiusKm(Number(event.target.value))}><option value={3}>3 km</option><option value={5}>5 km</option><option value={10}>10 km</option><option value={20}>20 km</option></select></label>
    </div>
    <div className="charger-density-map">
      <MapContainer center={center} zoom={10} scrollWheelZoom className="marketplace-leaflet-map">
        <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        <MapBoundsSync points={points} />
        {activeStations.map(station => <CircleMarker key={`station-${station.id}`} center={[station.latitude, station.longitude]} radius={5} pathOptions={{ color: '#334155', fillColor: '#64748b', fillOpacity: .8, weight: 2 }}><Popup><strong>{station.name}</strong><br />{station.city || station.address}<br />{station.availableSlots}/{station.totalSlots} slots available</Popup></CircleMarker>)}
        {density.map(({ property, stationCount }) => { const tone = densityTone(stationCount); return <CircleMarker key={`property-${property.id}`} center={[property.latitude, property.longitude]} radius={13} pathOptions={{ color: '#fff', fillColor: tone.color, fillOpacity: .9, weight: 3 }}><Popup><div className="density-map-popup"><strong>{property.title}</strong><span>{property.city || property.address}</span><b style={{ color: tone.color }}>{tone.label} · {stationCount} active within {radiusKm} km</b><small>{property.parkingBays} bays · {property.availableLoadKw} kW grid load</small><button type="button" onClick={() => onContact(property)}>Review Host site</button></div></Popup></CircleMarker>; })}
      </MapContainer>
    </div>
    {!opportunities.length && <p className="density-map-empty">No verified Host land is available for density analysis yet.</p>}
  </section>;
}

function MapBoundsSync({ points }: { points: [number, number][] }) {
  const map = useMap();
  const pointsKey = points.map(point => point.join(',')).join('|');
  useEffect(() => {
    if (!points.length) return;
    if (points.length === 1) map.setView(points[0], 12);
    else map.fitBounds(L.latLngBounds(points), { padding: [28, 28], maxZoom: 12 });
  }, [map, points, pointsKey]);
  return null;
}
