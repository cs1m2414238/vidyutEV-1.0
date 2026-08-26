import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowRight,
  BatteryCharging,
  Bot,
  BrainCircuit,
  CarFront,
  Check,
  CircleCheckBig,
  Clock3,
  Eye,
  IndianRupee,
  Gauge,
  LoaderCircle,
  MapPin,
  Navigation,
  RadioTower,
  RefreshCw,
  Route,
  ShieldCheck,
  Sparkles,
  Star,
  WalletCards,
  Wifi,
  Zap,
} from 'lucide-react';
import {
  addAutopilotVehicle,
  approveAutopilotReroute,
  completeAutopilotCharging,
  getAutopilotVehicles,
  getCurrentAutopilotTrip,
  launchAutopilotTrip,
  parseAutopilotJourneyIntent,
  previewAutopilotTrip,
  recommendAutopilotVehicle,
  recordAutopilotExperience,
  sendAutopilotAgentMessage,
  simulateAutopilotFault,
  startAutopilotTrip,
  topUpAutopilotWallet,
  type AutopilotMode,
  type AutopilotPlan,
  type AutopilotTrip,
  type AutopilotTripRequest,
  type TripPurpose,
  type AutopilotVehicle,
  type JourneyIntent,
  type VehicleRecommendation,
} from '../services/autopilot';
import { MapContainer, TileLayer, Marker as LeafletMarker, Popup as LeafletPopup, Polyline as LeafletPolyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import './AutopilotView.css';

interface AutopilotViewProps {
  token: string;
  userName: string;
  onOpenWallet: () => void;
}

interface PlanningInputs {
  origin: string;
  destination: string;
  deadline: string;
  battery: number;
  minimumBattery: number;
  budget: number;
  optimizeFor: AutopilotTripRequest['optimizeFor'];
  autonomyMode: AutopilotMode;
  tripPurpose: TripPurpose;
}

function JourneyMapController({ bounds }: { bounds: L.LatLngBoundsExpression }) {
  const map = useMap();
  useEffect(() => {
    map.invalidateSize();
    try {
      map.fitBounds(bounds, { padding: [35, 35], maxZoom: 14 });
    } catch {
      // ignore
    }
    const timer = setTimeout(() => {
      map.invalidateSize();
      try {
        map.fitBounds(bounds, { padding: [35, 35], maxZoom: 14 });
      } catch {
        // ignore
      }
    }, 250);
    return () => clearTimeout(timer);
  }, [map, bounds]);
  return null;
}

const CITY_COORDS: Record<string, [number, number]> = {
  kanpur: [26.4499, 80.3319],
  bhopal: [23.2599, 77.4126],
  lucknow: [26.8467, 80.9462],
  delhi: [28.6139, 77.2090],
  'new delhi': [28.6139, 77.2090],
  dausa: [26.8924, 76.3377],
  'sawai madhopur': [25.9928, 76.3712],
  sawai: [25.9928, 76.3712],
  madhopur: [25.9928, 76.3712],
  kota: [25.1819, 75.8362],
  mathura: [27.4924, 77.6737],
  indore: [22.7196, 75.8577],
  noida: [28.5355, 77.3910],
  gurugram: [28.4595, 77.0266],
  gurgaon: [28.4595, 77.0266],
  agra: [27.1767, 78.0081],
  jaipur: [26.9124, 75.7873],
  jhansi: [25.4484, 78.5685],
  gwalior: [26.2183, 78.1828],
  varanasi: [25.3176, 82.9739],
  prayagraj: [25.4358, 81.8463],
  allahabad: [25.4358, 81.8463],
  mumbai: [19.0760, 72.8777],
  pune: [18.5204, 73.8567],
  bangalore: [12.9716, 77.5946],
  bengaluru: [12.9716, 77.5946],
  hyderabad: [17.3850, 78.4867],
  chennai: [13.0827, 80.2707],
  kolkata: [22.5726, 88.3639],
};

function resolveCoordinate(nameOrAddress: string, fallbackLat?: number, fallbackLng?: number): [number, number] {
  if (typeof fallbackLat === 'number' && typeof fallbackLng === 'number' && fallbackLat !== 0 && fallbackLng !== 0) {
    return [fallbackLat, fallbackLng];
  }
  const clean = (nameOrAddress || '').toLowerCase().trim();
  for (const [key, coords] of Object.entries(CITY_COORDS)) {
    if (clean.includes(key)) return coords;
  }
  const match = clean.match(/(-?\d+(\.\d+)?),\s*(-?\d+(\.\d+)?)/);
  if (match) {
    return [parseFloat(match[1]), parseFloat(match[3])];
  }
  return [26.4499, 80.3319];
}

interface MapStop {
  id?: number | string;
  stationName: string;
  stationAddress?: string;
  latitude?: number;
  longitude?: number;
  powerKw?: number;
  status?: string;
  arrivalBatteryPercent?: number;
  targetBatteryPercent?: number;
  estimatedCost?: number;
  chargingMinutes?: number;
  selectionReason?: string;
  selectionType?: string;
  replacesStationId?: number;
  replacesStationName?: string;
  additionalMinutes?: number;
  additionalCost?: number;
  removalReason?: string;
  replacedByStationId?: number;
  replacedByStationName?: string;
  originalStopIndex?: number;
}

function AutopilotJourneyMap({
  origin,
  destination,
  stops,
  cancelledStop,
  proposedReplacement,
  title = 'Autonomous Journey Route & Live Telemetry',
}: {
  origin: string;
  destination: string;
  stops: MapStop[];
  cancelledStop?: { stationName: string; latitude?: number; longitude?: number; stationAddress?: string } | null;
  proposedReplacement?: { stationName: string; latitude?: number; longitude?: number; stationAddress?: string; estimatedCost?: number } | null;
  title?: string;
}) {
  const firstStop = stops.length > 0 ? stops[0] : undefined;
  const lastStop = stops.length > 0 ? stops[stops.length - 1] : undefined;
  const originCoord = useMemo(
    () => resolveCoordinate(origin, firstStop?.latitude != null ? firstStop.latitude + 0.5 : undefined, firstStop?.longitude != null ? firstStop.longitude - 0.5 : undefined),
    [origin, firstStop]
  );
  const destCoord = useMemo(
    () => resolveCoordinate(destination, lastStop?.latitude != null ? lastStop.latitude - 0.5 : undefined, lastStop?.longitude != null ? lastStop.longitude + 0.5 : undefined),
    [destination, lastStop]
  );

  const validStops = useMemo(() => {
    return stops.map((s, idx) => {
      let lat = s.latitude;
      let lng = s.longitude;
      if (!lat || !lng || (lat === 0 && lng === 0)) {
        const resolved = resolveCoordinate(s.stationAddress || s.stationName);
        if (resolved[0] !== 26.4499 || resolved[1] !== 80.3319) {
          lat = resolved[0];
          lng = resolved[1];
        } else {
          const fraction = (idx + 1) / (stops.length + 1);
          lat = originCoord[0] + (destCoord[0] - originCoord[0]) * fraction + (Math.sin(fraction * Math.PI) * 0.15);
          lng = originCoord[1] + (destCoord[1] - originCoord[1]) * fraction + (Math.cos(fraction * Math.PI) * 0.15);
        }
      }
      return { ...s, latitude: lat, longitude: lng };
    });
  }, [stops, originCoord, destCoord]);

  const routePolyline: [number, number][] = useMemo(() => {
    const activeStops = validStops.filter((s) => s.status !== 'CANCELLED');
    return [originCoord, ...activeStops.map((s) => [s.latitude!, s.longitude!] as [number, number]), destCoord];
  }, [originCoord, destCoord, validStops]);

  const cancelledStopPoint = useMemo(() => {
    const failed = validStops.find((s) => s.status === 'CANCELLED' || Boolean(s.removalReason));
    if (failed?.latitude && failed?.longitude) return [failed.latitude, failed.longitude] as [number, number];
    if (cancelledStop) {
      const resolved = resolveCoordinate(cancelledStop.stationAddress || cancelledStop.stationName, cancelledStop.latitude, cancelledStop.longitude);
      return resolved;
    }
    return null;
  }, [validStops, cancelledStop]);

  const allPoints: [number, number][] = useMemo(() => {
    const pts = [originCoord, ...validStops.map((s) => [s.latitude!, s.longitude!] as [number, number]), destCoord];
    if (cancelledStopPoint) {
      pts.push(cancelledStopPoint);
    }
    if (proposedReplacement?.latitude && proposedReplacement?.longitude) {
      pts.push([proposedReplacement.latitude, proposedReplacement.longitude]);
    }
    return pts;
  }, [originCoord, destCoord, validStops, cancelledStopPoint, proposedReplacement]);

  const bounds: L.LatLngBoundsExpression = useMemo(() => {
    if (allPoints.length === 0) return [[26.4499, 80.3319], [23.2599, 77.4126]];
    const lats = allPoints.map((p) => p[0]);
    const lngs = allPoints.map((p) => p[1]);
    return [
      [Math.min(...lats), Math.min(...lngs)],
      [Math.max(...lats), Math.max(...lngs)],
    ];
  }, [allPoints]);

  return (
    <div style={{ background: '#0F172A', borderRadius: 16, padding: '14px', border: '1px solid #1E293B', marginBottom: 20, boxShadow: '0 8px 30px rgba(0,0,0,0.25)' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ width: 28, height: 28, borderRadius: 8, background: 'rgba(0, 168, 107, 0.15)', color: '#00A86B', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800 }}>
            <Route size={16} />
          </div>
          <div>
            <div style={{ color: '#F8FAFC', fontSize: 13, fontWeight: 700 }}>{title}</div>
            <div style={{ color: '#94A3B8', fontSize: 11 }}>
              {origin} → {destination} · {validStops.length} charging waypoint{validStops.length === 1 ? '' : 's'}
            </div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 6, fontSize: 10, fontWeight: 700 }}>
          <span style={{ background: 'rgba(0, 168, 107, 0.2)', color: '#00A86B', padding: '3px 8px', borderRadius: 6, border: '1px solid rgba(0, 168, 107, 0.3)' }}>
            ● Active Route
          </span>
          {(cancelledStop || cancelledStopPoint) && (
            <span style={{ background: 'rgba(239, 68, 68, 0.2)', color: '#EF4444', padding: '3px 8px', borderRadius: 6, border: '1px solid rgba(239, 68, 68, 0.3)' }}>
              ✕ Failed / Removed
            </span>
          )}
          {validStops.some((s) => s.selectionType === 'REROUTED_REPLACEMENT' || s.replacesStationName) && (
            <span style={{ background: 'rgba(147, 51, 234, 0.2)', color: '#C084FC', padding: '3px 8px', borderRadius: 6, border: '1px solid rgba(147, 51, 234, 0.3)' }}>
              🔄 Auto-Rerouted
            </span>
          )}
        </div>
      </div>

      <div style={{ height: 320, width: '100%', borderRadius: 12, overflow: 'hidden', position: 'relative' }}>
        <MapContainer
          center={originCoord}
          zoom={8}
          scrollWheelZoom={false}
          style={{ height: '100%', width: '100%' }}
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/">CARTO</a>'
            url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
            subdomains="abcd"
            maxZoom={19}
          />
          <JourneyMapController bounds={bounds} />

          {/* Active Solid Polyline */}
          <LeafletPolyline
            positions={routePolyline}
            pathOptions={{ color: '#00A86B', weight: 4.5, opacity: 0.9 }}
          />

          {/* Cancelled Faded / Dashed Detour Polyline */}
          {cancelledStopPoint && (
            <LeafletPolyline
              positions={[originCoord, cancelledStopPoint]}
              pathOptions={{ color: '#EF4444', weight: 3, opacity: 0.6, dashArray: '5, 6' }}
            />
          )}

          {/* Origin Marker */}
          <LeafletMarker
            position={originCoord}
            icon={L.divIcon({
              className: 'origin-marker',
              html: `<div style="background: #3B82F6; color: #fff; border: 2.5px solid #fff; border-radius: 50%; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 11px; box-shadow: 0 4px 12px rgba(59, 130, 246, 0.5);">📍</div>`,
              iconSize: [32, 32],
              iconAnchor: [16, 16],
            })}
          >
            <LeafletPopup>
              <strong>Origin: {origin}</strong><br/>
              <span>Journey Start Point</span>
            </LeafletPopup>
          </LeafletMarker>

          {/* Destination Marker */}
          <LeafletMarker
            position={destCoord}
            icon={L.divIcon({
              className: 'dest-marker',
              html: `<div style="background: #0F172A; color: #fff; border: 2.5px solid #fff; border-radius: 50%; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.4);">🏁</div>`,
              iconSize: [32, 32],
              iconAnchor: [16, 16],
            })}
          >
            <LeafletPopup>
              <strong>Destination: {destination}</strong><br/>
              <span>Journey Final Endpoint</span>
            </LeafletPopup>
          </LeafletMarker>

          {/* Charging Stops */}
          {validStops.map((stop, i) => {
            const isFaulted = stop.status === 'CANCELLED' || Boolean(stop.removalReason);
            const isReplacement = stop.selectionType === 'REROUTED_REPLACEMENT'
              || Boolean(stop.replacesStationName)
              || (proposedReplacement && stop.stationName === proposedReplacement.stationName);
            const isCompleted = stop.status === 'COMPLETED';
            const isCharging = stop.status === 'CHARGING';
            const isReserved = stop.status === 'RESERVED';

            const pinColor = isFaulted
              ? '#EF4444'
              : isReplacement
                ? '#9333EA'
                : isCompleted
                  ? '#64748B'
                  : isCharging
                    ? '#3B82F6'
                    : '#00A86B';

            const pinIcon = isFaulted ? '✕' : isReplacement ? '🔄' : '⚡';
            const pinGlow = isFaulted
              ? '0 0 16px rgba(239, 68, 68, 0.7)'
              : isReplacement
                ? '0 0 18px rgba(147, 51, 234, 0.75)'
                : '0 4px 12px rgba(0,0,0,0.3)';

            return (
              <LeafletMarker
                key={`${stop.stationName}-${i}`}
                position={[stop.latitude!, stop.longitude!]}
                icon={L.divIcon({
                  className: 'stop-marker',
                  html: `<div style="background: ${pinColor}; color: #fff; border: 2.5px solid #fff; border-radius: 50%; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 13px; box-shadow: ${pinGlow}; cursor: pointer;">${pinIcon}</div>`,
                  iconSize: [32, 32],
                  iconAnchor: [16, 16],
                })}
              >
                <LeafletPopup>
                  <div style={{ padding: 4, minWidth: 220 }}>
                    {isFaulted ? (
                      <>
                        <div style={{ background: 'rgba(239, 68, 68, 0.15)', color: '#EF4444', fontSize: 10, fontWeight: 800, padding: '2px 6px', borderRadius: 4, display: 'inline-block', marginBottom: 4 }}>
                          ❌ REMOVED FROM JOURNEY
                        </div>
                        <strong style={{ fontSize: 13, color: '#0F172A', display: 'block' }}>{stop.stationName}</strong>
                        <div style={{ fontSize: 11, color: '#EF4444', fontWeight: 700, marginTop: 4 }}>Charger fault detected</div>
                        <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>
                          Replaced by <strong style={{ color: '#9333EA' }}>{stop.replacedByStationName || 'Sawai Madhopur District Demo Hub'}</strong>
                        </div>
                      </>
                    ) : isReplacement ? (
                      <>
                        <div style={{ background: 'rgba(147, 51, 234, 0.15)', color: '#9333EA', fontSize: 10, fontWeight: 800, padding: '2px 6px', borderRadius: 4, display: 'inline-block', marginBottom: 4 }}>
                          🔄 REPLACEMENT STOP
                        </div>
                        <strong style={{ fontSize: 13, color: '#0F172A', display: 'block' }}>{stop.stationName}</strong>
                        <div style={{ fontSize: 11, color: '#9333EA', fontWeight: 600, marginTop: 3 }}>
                          Selected automatically after {stop.replacesStationName || 'Dausa'} failure
                        </div>
                        <div style={{ fontSize: 11, color: '#334155', marginTop: 3 }}>
                          +{stop.additionalMinutes ?? 7} min impact · Arrive {stop.arrivalBatteryPercent}% → Leave {stop.targetBatteryPercent}%
                        </div>
                        {stop.estimatedCost != null && <div style={{ fontSize: 11, fontWeight: 700, color: '#00A86B', marginTop: 3 }}>₹{stop.estimatedCost.toFixed(0)}</div>}
                      </>
                    ) : (
                      <>
                        <strong style={{ fontSize: 13, color: '#0F172A' }}>{stop.stationName}</strong>
                        {stop.stationAddress && <div style={{ fontSize: 11, color: '#64748B' }}>{stop.stationAddress}</div>}
                        <div style={{ fontSize: 11, fontWeight: 700, color: pinColor, marginTop: 4 }}>
                          Status: {isReserved ? '⚡ NEXT STOP · RESERVED' : (stop.status || 'PLANNED')}
                        </div>
                        {stop.arrivalBatteryPercent != null && stop.targetBatteryPercent != null && (
                          <div style={{ fontSize: 11, color: '#334155', marginTop: 2 }}>
                            Charge: {stop.arrivalBatteryPercent}% → {stop.targetBatteryPercent}%
                          </div>
                        )}
                        {stop.powerKw && <div style={{ fontSize: 11, color: '#64748B' }}>Power: {stop.powerKw} kW</div>}
                        {stop.estimatedCost != null && <div style={{ fontSize: 11, fontWeight: 700, color: '#00A86B' }}>₹{stop.estimatedCost.toFixed(0)}</div>}
                      </>
                    )}
                  </div>
                </LeafletPopup>
              </LeafletMarker>
            );
          })}
        </MapContainer>
      </div>
    </div>
  );
}

export function AutopilotView({ token, userName, onOpenWallet }: AutopilotViewProps) {
  const [trip, setTrip] = useState<AutopilotTrip | null>(null);
  const [vehicles, setVehicles] = useState<AutopilotVehicle[]>([]);
  const [vehicleId, setVehicleId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [action, setAction] = useState('');
  const [error, setError] = useState('');
  const [plannerError, setPlannerError] = useState('');
  const [agentReply, setAgentReply] = useState('');
  const [agentToolCalls, setAgentToolCalls] = useState<Array<{ name: string; status: string }>>([]);
  const [proposal, setProposal] = useState<AutopilotPlan | null>(null);
  const [vehicleRecommendation, setVehicleRecommendation] = useState<VehicleRecommendation | null>(null);
  const [agentSessionId, setAgentSessionId] = useState<string>();
  const [goal, setGoal] = useState('');
  const [intentFeedback, setIntentFeedback] = useState('');
  const [parsedIntentText, setParsedIntentText] = useState('');
  const [origin, setOrigin] = useState('');
  const [destination, setDestination] = useState('');
  const [deadline, setDeadline] = useState('');
  const [battery, setBattery] = useState(50);
  const [minimumBattery, setMinimumBattery] = useState(15);
  const [budget, setBudget] = useState(1000);
  const [optimizeFor, setOptimizeFor] = useState<AutopilotTripRequest['optimizeFor']>('TIME');
  const [autonomyMode, setAutonomyMode] = useState<AutopilotMode>('ASK_BEFORE_ACTIONS');
  const [tripPurpose, setTripPurpose] = useState<TripPurpose>('GENERAL');
  const [vehicleForm, setVehicleForm] = useState({
    makeAndModel: 'Tata Nexon EV',
    registrationNumber: '',
    batteryCapacity: '40.5 kWh',
    connectorType: 'CCS2',
    maxDcChargePowerKw: 50,
    chargingEfficiency: 0.9,
  });

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [availableVehicles, currentTrip] = await Promise.all([
        getAutopilotVehicles(token),
        getCurrentAutopilotTrip(token),
      ]);
      setVehicles(availableVehicles);
      const active = availableVehicles[0];
      setVehicleId((current) => current ?? active?.id ?? null);
      if (active?.batteryPercent != null) setBattery(active.batteryPercent);
      setTrip(currentTrip);
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const activeStop = useMemo(
    () => trip?.stops.find((stop) => stop.status === 'RESERVED') ?? null,
    [trip],
  );

  const cancelledStop = useMemo(
    () => {
      if (!trip) return null;
      const activeReplacement = trip.stops.filter((stop) => stop.stationId === trip.activeStationId
        && (stop.status === 'PLANNED' || stop.status === 'RESERVED')).at(-1);
      const activeReplacementCancelledStop = activeReplacement
        ? trip.stops.filter((stop) => stop.status === 'CANCELLED'
          && stop.sequenceNumber === activeReplacement.sequenceNumber).at(-1)
        : null;
      return activeReplacementCancelledStop
        ?? trip.stops.filter((stop) => stop.status === 'CANCELLED').at(-1)
        ?? null;
    },
    [trip],
  );

  const pairedReplacement = useMemo(
    () => cancelledStop
      ? trip?.stops.filter((stop) => stop.id !== cancelledStop.id
        && stop.sequenceNumber === cancelledStop.sequenceNumber
        && stop.status !== 'CANCELLED').at(-1) ?? null
      : null,
    [trip, cancelledStop],
  );

  const proposedReplacement = useMemo(
    () => pairedReplacement?.status === 'PLANNED'
      ? pairedReplacement
      : trip?.stops.filter((stop) => stop.status === 'PLANNED').at(-1) ?? null,
    [trip, pairedReplacement],
  );

  const reservedReplacement = pairedReplacement?.status === 'RESERVED' ? pairedReplacement : null;

  const rerouteImpact = useMemo(() => {
    const replacement = pairedReplacement;
    if (!trip || !cancelledStop || !replacement) return null;
    const extraDistanceKm = Math.max(0, replacement.routeOffsetKm - cancelledStop.routeOffsetKm);
    const originalStopMinutes = cancelledStop.estimatedWaitMinutes + cancelledStop.chargingMinutes + cancelledStop.connectionMinutes;
    const replacementStopMinutes = replacement.estimatedWaitMinutes + replacement.chargingMinutes + replacement.connectionMinutes;
    const delayMinutes = Math.max(0, replacementStopMinutes - originalStopMinutes + Math.ceil(extraDistanceKm / 0.8));
    const vehicle = vehicles.find((item) => item.id === trip.telemetry.vehicleId);
    const efficiencyWhPerKm = vehicle?.efficiencyWhPerKm ?? 160;
    const extraBatteryPercent = trip.telemetry.batteryCapacityKwh > 0
      ? extraDistanceKm * efficiencyWhPerKm / 1000 / trip.telemetry.batteryCapacityKwh * 100
      : 0;
    return {
      extraDistanceKm: Number(extraDistanceKm.toFixed(1)),
      delayMinutes,
      chargingCostDifference: Number((replacement.estimatedCost - cancelledStop.estimatedCost).toFixed(0)),
      extraBatteryPercent: Number(extraBatteryPercent.toFixed(1)),
    };
  }, [trip, cancelledStop, pairedReplacement, vehicles]);

  const selectVehicle = (nextVehicleId: number) => {
    setVehicleId(nextVehicleId);
    const selected = vehicles.find((vehicle) => vehicle.id === nextVehicleId);
    if (selected?.batteryPercent != null) setBattery(selected.batteryPercent);
    setVehicleRecommendation(null);
    setProposal(null);
  };

  const currentPlanningInputs = (): PlanningInputs => ({
    origin,
    destination,
    deadline,
    battery,
    minimumBattery,
    budget,
    optimizeFor,
    autonomyMode,
    tripPurpose,
  });

  const applyJourneyIntent = (intent: JourneyIntent, current: PlanningInputs): PlanningInputs => {
    const next: PlanningInputs = {
      origin: intent.origin?.trim() || current.origin,
      destination: intent.destination?.trim() || current.destination,
      deadline: intent.arrivalDeadline || current.deadline,
      battery: intent.currentBatteryPercent ?? current.battery,
      minimumBattery: intent.minimumArrivalBatteryPercent ?? current.minimumBattery,
      budget: intent.maximumChargingBudget ?? current.budget,
      optimizeFor: intent.optimizeFor ?? current.optimizeFor,
      autonomyMode: intent.autonomyMode ?? current.autonomyMode,
      tripPurpose: intent.tripPurpose ?? current.tripPurpose,
    };
    setOrigin(next.origin);
    setDestination(next.destination);
    setDeadline(next.deadline);
    setBattery(next.battery);
    setMinimumBattery(next.minimumBattery);
    setBudget(next.budget);
    setOptimizeFor(next.optimizeFor);
    setAutonomyMode(next.autonomyMode);
    setTripPurpose(next.tripPurpose);
    return next;
  };

  const resolveJourneyIntent = async (showFeedback = false, force = false): Promise<PlanningInputs> => {
    const current = currentPlanningInputs();
    if (!goal.trim()) return current;
    if (!force && parsedIntentText === goal.trim()) return current;
    try {
      const intent = await parseAutopilotJourneyIntent(token, goal.trim());
      const next = applyJourneyIntent(intent, current);
      setParsedIntentText(goal.trim());
      if (showFeedback || intent.recognizedFields.length > 0) {
        setIntentFeedback(intent.recognizedFields.length > 0
          ? `Applied ${intent.recognizedFields.length} detail${intent.recognizedFields.length === 1 ? '' : 's'} from your request. Review the fields below before planning.`
          : 'No structured trip details were recognized. Add “from … to …”, battery, reserve, budget, or arrival time.');
      }
      return next;
    } catch (requestError) {
      if (showFeedback) setPlannerError(messageFor(requestError));
      return current;
    }
  };

  const interpretGoal = async () => {
    if (!goal.trim()) {
      setPlannerError('Describe the journey first—for example, “Kanpur to Bhopal, battery 76%, reserve 10%, under ₹900, fastest.”');
      return;
    }
    setAction('parse-intent');
    setPlannerError('');
    try {
      await resolveJourneyIntent(true, true);
    } finally {
      setAction('');
    }
  };

  const buildTripRequest = (inputs: PlanningInputs = currentPlanningInputs()): AutopilotTripRequest => ({
    vehicleId: vehicleId ?? 0,
    origin: inputs.origin.trim(),
    destination: inputs.destination.trim(),
    goal: goal.trim(),
    tripPurpose: inputs.tripPurpose,
    arrivalDeadline: inputs.deadline,
    optimizeFor: inputs.optimizeFor,
    autonomyMode: inputs.autonomyMode,
    currentBatteryPercent: inputs.battery,
    minimumArrivalBatteryPercent: inputs.minimumBattery,
    maximumChargingBudget: inputs.budget,
    idempotencyKey: typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `TRIP-${Date.now()}`,
  });

  const planningInputsAreValid = (inputs: PlanningInputs = currentPlanningInputs()): boolean => {
    if (!vehicleId) {
      setPlannerError('Add an EV before planning the journey.');
      return false;
    }
    if (!inputs.origin.trim() || !inputs.destination.trim()) {
      setPlannerError('Enter both the starting place and destination.');
      return false;
    }
    if (inputs.battery <= inputs.minimumBattery) {
      setPlannerError('Current battery must be above the arrival reserve.');
      return false;
    }
    if (inputs.budget <= 0) {
      setPlannerError('Enter a charging budget greater than zero.');
      return false;
    }
    return true;
  };

  const runAction = async (name: string, operation: () => Promise<AutopilotTrip>) => {
    setAction(name);
    setError('');
    try {
      setTrip(await operation());
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setAction('');
    }
  };

  const planWithAgent = async () => {
    const inputs = await resolveJourneyIntent();
    if (!planningInputsAreValid(inputs)) return;
    setAction('agent');
    setPlannerError('');
    setAgentReply('');
    setAgentToolCalls([]);
    setVehicleRecommendation(null);
    setProposal(null);
    try {
      const userPrompt = goal.trim()
        ? `Driver request: "${goal.trim()}". Check vehicle ID ${vehicleId} status and generate a read-only Autopilot proposal with preview_autopilot_trip. Enforce the parsed application values: from ${inputs.origin} to ${inputs.destination}, current battery ${inputs.battery}%, arrival reserve ${inputs.minimumBattery}%, budget ₹${inputs.budget}, arrival deadline ${inputs.deadline || 'none'}, purpose ${inputs.tripPurpose}, optimize for ${inputs.optimizeFor}, autonomy mode ${inputs.autonomyMode}.`
        : `Create a read-only Vidyut Autopilot proposal. First call get_vehicle_status for vehicle ID ${vehicleId}, then call preview_autopilot_trip. Route: ${inputs.origin} to ${inputs.destination}. Current battery: ${inputs.battery}%. Arrival deadline: ${inputs.deadline || 'none'}. Minimum arrival reserve: ${inputs.minimumBattery}%. Maximum charging budget: INR ${inputs.budget}. Trip purpose: ${inputs.tripPurpose}. Optimize for: ${inputs.optimizeFor}. Autonomy mode: ${inputs.autonomyMode}.`;

      const response = await sendAutopilotAgentMessage(
        token,
        userPrompt,
        agentSessionId,
        buildTripRequest(inputs),
      );
      setAgentSessionId(response.sessionId);
      setAgentReply(response.reply);
      setAgentToolCalls(response.toolCalls);
      setProposal(response.plan ?? await previewAutopilotTrip(token, buildTripRequest(inputs)));
    } catch (requestError) {
      setPlannerError(messageFor(requestError));
    } finally {
      setAction('');
    }
  };

  const quickPreview = async () => {
    const inputs = await resolveJourneyIntent();
    if (!planningInputsAreValid(inputs)) return;
    setAction('preview');
    setPlannerError('');
    setAgentReply('');
    setAgentToolCalls([]);
    setVehicleRecommendation(null);
    try {
      setProposal(await previewAutopilotTrip(token, buildTripRequest(inputs)));
    } catch (requestError) {
      setPlannerError(messageFor(requestError));
    } finally {
      setAction('');
    }
  };

  const chooseBestVehicle = async () => {
    if (!vehicles.length) {
      setPlannerError('Add an EV before comparing your garage.');
      return;
    }
    const inputs = await resolveJourneyIntent();
    if (!inputs.origin.trim() || !inputs.destination.trim()) {
      setPlannerError('Enter both the starting place and destination.');
      return;
    }
    if (inputs.budget <= 0) {
      setPlannerError('Enter a charging budget greater than zero.');
      return;
    }
    setAction('recommend-vehicle');
    setPlannerError('');
    setAgentReply('');
    setAgentToolCalls([]);
    setProposal(null);
    try {
      const recommendation = await recommendAutopilotVehicle(token, {
        origin: inputs.origin.trim(),
        destination: inputs.destination.trim(),
        goal: goal.trim(),
        tripPurpose: inputs.tripPurpose,
        arrivalDeadline: inputs.deadline,
        optimizeFor: inputs.optimizeFor,
        autonomyMode: inputs.autonomyMode,
        fallbackBatteryPercent: inputs.battery,
        minimumArrivalBatteryPercent: inputs.minimumBattery,
        maximumChargingBudget: inputs.budget,
      });
      setVehicleRecommendation(recommendation);
      if (recommendation.recommendedVehicleId) {
        setVehicleId(recommendation.recommendedVehicleId);
        const selected = recommendation.vehicles.find(
          (vehicle) => vehicle.vehicleId === recommendation.recommendedVehicleId,
        );
        if (selected) setBattery(selected.currentBatteryPercent);
      }
      setProposal(recommendation.recommendedPlan ?? null);
    } catch (requestError) {
      setPlannerError(messageFor(requestError));
    } finally {
      setAction('');
    }
  };

  const confirmProposal = async () => {
    if (!vehicleId || !proposal || autonomyMode === 'RECOMMEND_ONLY' || !proposal.overallFeasible) return;
    setAction('confirm');
    setError('');
    try {
      // The user has approved the reviewed proposal, so execute its typed,
      // deterministic Spring command instead of asking the agent to repeat it.
      const createdTrip = await launchAutopilotTrip(token, {
        vehicleId: proposal.vehicleId,
        origin: proposal.origin,
        destination: proposal.destination,
        goal: goal.trim(),
        tripPurpose: proposal.tripPurpose,
        arrivalDeadline: proposal.arrivalDeadline ?? '',
        optimizeFor: proposal.optimizeFor,
        autonomyMode,
        currentBatteryPercent: proposal.currentBatteryPercent,
        minimumArrivalBatteryPercent: proposal.minimumArrivalBatteryPercent,
        maximumChargingBudget: proposal.maximumChargingBudget,
        idempotencyKey: typeof crypto !== 'undefined' && 'randomUUID' in crypto
          ? crypto.randomUUID()
          : `TRIP-${Date.now()}`,
      });
      if (!createdTrip) {
        setError(
          'The reservation was not created. Your plan is still available—try Confirm Autopilot again.',
        );
        return;
      }
      setTrip(createdTrip);
      setProposal(null);
    } catch (requestError) {
      setPlannerError(messageFor(requestError));
    } finally {
      setAction('');
    }
  };

  const useRequiredBudget = () => {
    if (!proposal) return;
    const requiredBudget = Math.ceil(proposal.estimatedChargingCost);
    setBudget(requiredBudget);
    setPlannerError('');
    setProposal({
      ...proposal,
      maximumChargingBudget: requiredBudget,
      budgetRemaining: requiredBudget - proposal.estimatedChargingCost,
      withinBudget: true,
      overallFeasible: proposal.safeArrivalReserve && proposal.deadlineFeasible,
    });
  };

  const addVehicle = async () => {
    if (!vehicleForm.registrationNumber.trim()) {
      setError('Enter the EV registration number.');
      return;
    }
    setAction('vehicle');
    setError('');
    try {
      const vehicle = await addAutopilotVehicle(token, {
        ...vehicleForm,
        registrationNumber: vehicleForm.registrationNumber.trim().toUpperCase(),
      });
      setVehicles((current) => [...current, vehicle]);
      setVehicleId(vehicle.id);
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setAction('');
    }
  };

  const topUp = async () => {
    setAction('topup');
    setError('');
    try {
      await topUpAutopilotWallet(token, 1000);
      if (trip) setTrip(await getCurrentAutopilotTrip(token));
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setAction('');
    }
  };

  const saveRouteExperience = async (
    name: string,
    outcome: 'EXCESS_WAIT' | 'ACCESS_ISSUE' | 'USER_REPORTED',
    detail: string,
    delayMinutes?: number,
  ) => {
    if (!trip) return;
    setAction(name);
    setError('');
    try {
      await recordAutopilotExperience(token, trip.id, {
        stationId: activeStop?.stationId,
        outcome,
        detail,
        delayMinutes,
      });
      setTrip(await getCurrentAutopilotTrip(token));
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setAction('');
    }
  };

  if (loading) {
    return (
      <div className="autopilot-loading">
        <LoaderCircle className="spin" size={28} />
        <span>Connecting vehicle, wallet and charging network…</span>
      </div>
    );
  }

  return (
    <div className="autopilot-page">
      <section className="autopilot-hero">
        <div className="autopilot-hero-copy">
          <div className="autopilot-kicker"><Sparkles size={14} /> VIDYUT AUTOPILOT</div>
          <h1>Give it your destination.<br /><span>Vidyut handles charging.</span></h1>
          <p>
            Welcome {userName.split(' ')[0]}. Autopilot plans, reserves, monitors, reroutes and pays while
            deterministic services protect range, budget and connector compatibility.
          </p>
          <div className="autopilot-trust-row">
            <span><ShieldCheck size={15} /> EV mode protected</span>
            <span><Route size={15} /> Live replanning</span>
            <span><WalletCards size={15} /> Vehicle-linked AutoPay</span>
          </div>
        </div>
        <div className="autopilot-orbit" aria-hidden="true">
          <div className="orbit-ring orbit-ring-one" />
          <div className="orbit-ring orbit-ring-two" />
          <div className="orbit-core"><Bot size={34} /></div>
          <span className="orbit-node node-route"><Navigation size={17} /></span>
          <span className="orbit-node node-charge"><Zap size={17} /></span>
          <span className="orbit-node node-wallet"><IndianRupee size={17} /></span>
        </div>
      </section>

      <div className="autopilot-layout">
        <div className="autopilot-main-column">
          {error && <div className="autopilot-error" role="alert"><AlertTriangle size={18} /> {error}</div>}
          <section className="autopilot-card goal-card">
            <div className="autopilot-card-heading">
              <div>
                <span className="step-number">01</span>
                <div><h2>Set the journey goal</h2><p>Natural-language intent plus enforceable safety constraints.</p></div>
              </div>
              {vehicles.length > 0 && (
                <select value={vehicleId ?? ''} onChange={(event) => selectVehicle(Number(event.target.value))}>
                  {vehicles.map((vehicle) => <option value={vehicle.id} key={vehicle.id}>{vehicle.makeAndModel} · {vehicle.registrationNumber}</option>)}
                </select>
              )}
            </div>

            {vehicles.length === 0 ? (
              <div className="vehicle-onboarding">
                <div className="vehicle-onboarding-icon"><CarFront size={25} /></div>
                <div className="vehicle-onboarding-copy"><h3>Connect your EV first</h3><p>Telemetry and connector compatibility are linked to this vehicle.</p></div>
                <div className="vehicle-form-grid">
                  <label>Make & model<input value={vehicleForm.makeAndModel} onChange={(event) => setVehicleForm((current) => ({ ...current, makeAndModel: event.target.value }))} /></label>
                  <label>Registration<input placeholder="UP78 AB 1234" value={vehicleForm.registrationNumber} onChange={(event) => setVehicleForm((current) => ({ ...current, registrationNumber: event.target.value }))} /></label>
                  <label>Battery<input value={vehicleForm.batteryCapacity} onChange={(event) => setVehicleForm((current) => ({ ...current, batteryCapacity: event.target.value }))} /></label>
                  <label>Connector<select value={vehicleForm.connectorType} onChange={(event) => setVehicleForm((current) => ({ ...current, connectorType: event.target.value }))}><option>CCS2</option><option>TYPE2</option><option>CHADEMO</option><option>GB_T</option><option>TYPE1</option></select></label>
                  <label>Maximum DC power<input type="number" min="1" value={vehicleForm.maxDcChargePowerKw} onChange={(event) => setVehicleForm((current) => ({ ...current, maxDcChargePowerKw: Number(event.target.value) }))} /></label>
                  <label>Charging efficiency<input type="number" min="0.5" max="1" step="0.01" value={vehicleForm.chargingEfficiency} onChange={(event) => setVehicleForm((current) => ({ ...current, chargingEfficiency: Number(event.target.value) }))} /></label>
                </div>
                <button className="autopilot-secondary-button" onClick={() => void addVehicle()} disabled={Boolean(action)}>
                  {action === 'vehicle' ? <LoaderCircle className="spin" size={16} /> : <CarFront size={16} />} Add EV
                </button>
              </div>
            ) : (
              <>
                <div className="autonomy-mode-block">
                  <div className="autonomy-mode-heading">
                    <div><ShieldCheck size={16} /><span>HOW SHOULD VIDYUT HELP?</span></div>
                    <small>Planning is always automatic. You control execution.</small>
                  </div>
                  <div className="autonomy-mode-grid simplified-mode-grid" role="radiogroup" aria-label="Autopilot mode">
                    <ModeButton
                      active={autonomyMode === 'RECOMMEND_ONLY'}
                      icon={<Eye size={17} />}
                      title="Recommend"
                      emoji="👁"
                      detail="Plans the journey. You handle bookings, payments and reroutes."
                      onClick={() => setAutonomyMode('RECOMMEND_ONLY')}
                    />
                    <ModeButton
                      active={autonomyMode === 'ASK_BEFORE_ACTIONS'}
                      recommended
                      icon={<ShieldCheck size={17} />}
                      title="Ask Me"
                      emoji="🛡"
                      detail="Plans automatically and asks before bookings, payments or major reroutes."
                      onClick={() => setAutonomyMode('ASK_BEFORE_ACTIONS')}
                    />
                    <ModeButton
                      active={autonomyMode === 'FULL_AUTOPILOT'}
                      icon={<Zap size={17} />}
                      title="Auto"
                      emoji="⚡"
                      detail="Handles the journey automatically within your battery, budget and safety limits."
                      onClick={() => setAutonomyMode('FULL_AUTOPILOT')}
                    />
                  </div>
                  <div className="autonomy-selected-summary">
                    {autonomyMode === 'RECOMMEND_ONLY' && (
                      <p><strong>Recommend</strong> — Plans the journey. You handle bookings, payments and reroutes.</p>
                    )}
                    {autonomyMode === 'ASK_BEFORE_ACTIONS' && (
                      <p><strong>Ask Me <span className="rec-pill">RECOMMENDED</span></strong> — Plans automatically and asks before bookings, payments or major reroutes.</p>
                    )}
                    {autonomyMode === 'FULL_AUTOPILOT' && (
                      <p><strong>Auto</strong> — Handles the journey automatically within your battery, budget and safety limits.</p>
                    )}
                  </div>
                </div>
                <label className="goal-prompt-label">
                  <span><Bot size={16} /> Tell Vidyut what matters</span>
                  <textarea
                    placeholder="Describe your route, battery reserve, budget, arrival time, and priorities..."
                    value={goal}
                    onChange={(event) => {
                      setGoal(event.target.value);
                      setIntentFeedback('');
                      setParsedIntentText('');
                    }}
                    rows={3}
                  />
                </label>
                <div className="intent-parser-row">
                  <button type="button" onClick={() => void interpretGoal()} disabled={Boolean(action)}>
                    {action === 'parse-intent' ? <LoaderCircle className="spin" size={15} /> : <Sparkles size={15} />}
                    {action === 'parse-intent' ? 'Reading request…' : 'Fill trip details from text'}
                  </button>
                  {intentFeedback && <span role="status">{intentFeedback}</span>}
                </div>
                <div className="trip-purpose-block">
                  <span>What should this stop support?</span>
                  <div>
                    {([
                      ['GENERAL', 'Flexible'], ['MALL_VISIT', 'Mall visit'], ['REST_STOP', 'Rest & food'],
                      ['COMMUTE', 'Daily commute'], ['DESTINATION_CHARGING', 'Charge near destination'],
                    ] as Array<[TripPurpose, string]>).map(([value, label]) => (
                      <button type="button" key={value} className={tripPurpose === value ? 'active' : ''} onClick={() => setTripPurpose(value)}>{label}</button>
                    ))}
                  </div>
                </div>
                <div className="route-input-row">
                  <label><span>From</span><div><MapPin size={16} /><input placeholder="City, address, or coordinates" value={origin} onChange={(event) => setOrigin(event.target.value)} /></div></label>
                  <ArrowRight size={18} className="route-arrow" />
                  <label><span>To</span><div><Navigation size={16} /><input placeholder="City, address, or coordinates" value={destination} onChange={(event) => setDestination(event.target.value)} /></div></label>
                </div>
                <div className="constraint-grid">
                  <label><span>Current battery</span><div><BatteryCharging size={16} /><input type="number" min="1" max="100" placeholder="50" value={battery} onChange={(event) => setBattery(Number(event.target.value))} /><strong>%</strong></div></label>
                  <label><span>Safety reserve</span><div><ShieldCheck size={16} /><input type="number" min="5" max="50" placeholder="15" value={minimumBattery} onChange={(event) => setMinimumBattery(Number(event.target.value))} /><strong>%</strong></div></label>
                  <label><span>Maximum budget</span><div><IndianRupee size={16} /><input type="number" min="1" placeholder="1000" value={budget} onChange={(event) => setBudget(Number(event.target.value))} /></div></label>
                  <label><span>Arrive by</span><div><Clock3 size={16} /><input type="time" value={deadline} onChange={(event) => setDeadline(event.target.value)} /></div></label>
                </div>
                <div className="goal-footer">
                  <div className="optimization-switch" aria-label="Optimization preference">
                    {([
                      ['TIME', 'Fastest', 'Minimize total trip time'],
                      ['BALANCED', 'Balanced', 'Time + cost + convenience'],
                      ['COST', 'Lowest cost', 'Minimize charging expense'],
                    ] as const).map(([option, label, detail]) => (
                      <button type="button" key={option} className={optimizeFor === option ? 'active' : ''} onClick={() => setOptimizeFor(option)}><strong>{label}</strong><small>{detail}</small></button>
                    ))}
                  </div>
                  <div className="agent-plan-actions">
                    <button className="autopilot-recommend-vehicle-button" onClick={() => void chooseBestVehicle()} disabled={Boolean(action)}>
                      {action === 'recommend-vehicle' ? <LoaderCircle className="spin" size={18} /> : <CarFront size={18} />}
                      {action === 'recommend-vehicle' ? 'Comparing garage…' : 'Choose my best car'}
                    </button>
                    <button className="autopilot-agent-button" onClick={() => void planWithAgent()} disabled={Boolean(action)}>
                      {action === 'agent' ? <LoaderCircle className="spin" size={18} /> : <Bot size={18} />}
                      {action === 'agent' ? 'Vidyut is planning…' : 'Build plan with Vidyut'}
                    </button>
                    <button className="autopilot-preview-button" onClick={() => void quickPreview()} disabled={Boolean(action)}>
                      {action === 'preview' ? <LoaderCircle className="spin" size={18} /> : <BrainCircuit size={18} />}
                      Quick preview
                    </button>
                  </div>
                </div>
                {action === 'agent' && (
                  <div className="planning-progress" aria-live="polite">
                    <span className="planning-pulse"><Bot size={18} /></span>
                    <div><strong>Building a feasible journey</strong><p>Reading vehicle range, scoring compatible chargers, and checking cost and arrival reserve.</p></div>
                    <div className="planning-dots"><i /><i /><i /></div>
                  </div>
                )}
                {plannerError && (
                  <div className="planner-inline-error" role="alert">
                    <AlertTriangle size={17} />
                    <span>{plannerError}</span>
                  </div>
                )}
              </>
            )}
          </section>

          {vehicleRecommendation && (
            <VehicleRecommendationPanel
              recommendation={vehicleRecommendation}
              onChoose={selectVehicle}
            />
          )}

          {proposal && (
            <AutopilotProposal
              plan={proposal}
              mode={autonomyMode}
              reply={agentReply}
              toolCalls={agentToolCalls}
              busy={action === 'confirm'}
              onConfirm={() => void confirmProposal()}
              onUseRequiredBudget={useRequiredBudget}
            />
          )}

          {trip && (
            <>
              {/* BIG REROUTING BANNER */}
              {(trip.status === 'REROUTED' || trip.status === 'REROUTE_APPROVAL_REQUIRED' || cancelledStop) && (
                <section className={`big-rerouting-banner ${trip.status === 'REROUTE_APPROVAL_REQUIRED' ? 'approval-mode' : 'auto-mode'}`} role="status" aria-live="polite">
                  <div className="reroute-banner-header">
                    <div className="reroute-banner-icon">
                      <AlertTriangle size={24} />
                    </div>
                    <div className="reroute-banner-title">
                      <span className="reroute-badge">⚠️ CHARGER UNAVAILABLE — VIDYUT REROUTED YOUR JOURNEY</span>
                      <h2><strong>{cancelledStop?.stationName || 'Dausa District Demo Hub'}</strong> is no longer available.</h2>
                    </div>
                  </div>

                  <div className="reroute-banner-body">
                    <div className="reroute-new-charger-card">
                      <div className="new-charger-label">
                        <RefreshCw size={14} className="spin-slow" />
                        <span>NEW CHARGER</span>
                      </div>
                      <h3>{reservedReplacement?.stationName || proposedReplacement?.stationName || (pairedReplacement?.stationName) || 'Sawai Madhopur District Demo Hub'}</h3>
                      <div className="new-charger-specs">
                        <span><Zap size={13} /> {reservedReplacement?.connectorType || proposedReplacement?.connectorType || 'CCS2'} · {reservedReplacement?.powerKw || proposedReplacement?.powerKw || 150} kW rated · 5 compatible connectors</span>
                        <span><BatteryCharging size={13} /> <strong>Arrive:</strong> {reservedReplacement?.arrivalBatteryPercent || proposedReplacement?.arrivalBatteryPercent || 18.2}% → <strong>New target: {reservedReplacement?.targetBatteryPercent || proposedReplacement?.targetBatteryPercent || 78}%</strong></span>
                        <span><Clock3 size={13} /> <strong>Route impact:</strong> +{rerouteImpact?.extraDistanceKm ?? (reservedReplacement?.additionalDistanceKm ?? 31)} km · +{rerouteImpact?.delayMinutes ?? (reservedReplacement?.additionalMinutes ?? 7)} min</span>
                        <span><ShieldCheck size={13} /> <strong>New ETA:</strong> {formatMinutes(trip.totalDurationMinutes)} · <strong>Arrival reserve:</strong> {trip.estimatedArrivalBatteryPercent}% ✅</span>
                      </div>
                    </div>

                    <div className="reroute-checklist">
                      <div className="checklist-item checked">
                        <Check size={14} />
                        <span>Old reservation cancelled (zero fee)</span>
                      </div>
                      <div className="checklist-item checked">
                        <Check size={14} />
                        <span>{trip.status === 'REROUTE_APPROVAL_REQUIRED' ? 'New connector ready to reserve' : 'New connector reserved'}</span>
                      </div>
                      <div className="checklist-item checked">
                        <Check size={14} />
                        <span>Navigation updated</span>
                      </div>
                    </div>
                  </div>

                  <div className="reroute-banner-footer">
                    {trip.status === 'REROUTE_APPROVAL_REQUIRED' ? (
                      <div className="approval-actions-row">
                        <button
                          type="button"
                          className="approve-reroute-btn"
                          disabled={action === 'approve-reroute'}
                          onClick={() => void runAction('approve-reroute', () => approveAutopilotReroute(token, trip.id))}
                        >
                          {action === 'approve-reroute' ? <LoaderCircle className="spin" size={16} /> : <Check size={16} />}
                          Approve new charger
                        </button>
                        <button
                          type="button"
                          className="see-alternatives-btn"
                          onClick={() => {
                            const el = document.querySelector('.stops-card');
                            el?.scrollIntoView({ behavior: 'smooth' });
                          }}
                        >
                          See alternatives
                        </button>
                      </div>
                    ) : trip.autonomyMode === 'FULL_AUTOPILOT' ? (
                      <div className="auto-confirmed-msg">
                        <Check size={16} />
                        <span>Automatically rerouted within your approved limits</span>
                      </div>
                    ) : (
                      <div className="recommend-actions-row">
                        <button
                          type="button"
                          className="navigate-replacement-btn"
                          onClick={() => {
                            const el = document.querySelector('.stops-card');
                            el?.scrollIntoView({ behavior: 'smooth' });
                          }}
                        >
                          <Navigation size={15} />
                          Navigate to replacement
                        </button>
                      </div>
                    )}
                  </div>
                </section>
              )}

              <section className="autopilot-card route-plan-card">
                <div className="route-plan-head">
                  <div><span className="step-number">02</span><div><h2>Your autonomous charging plan</h2><p>{trip.origin} → {trip.destination}</p></div></div>
                  <span className={`trip-status status-${trip.status.toLowerCase()}`}>{statusLabel(trip.status)}</span>
                </div>
                <div className="trip-metrics">
                  <TripMetric icon={<Route />} value={`${trip.totalDistanceKm} km`} label="Road distance" />
                  <TripMetric icon={<Clock3 />} value={formatMinutes(trip.totalDurationMinutes)} label="Door-to-door" />
                  <TripMetric icon={<IndianRupee />} value={`₹${trip.estimatedChargingCost.toFixed(0)}`} label={`of ₹${trip.maximumChargingBudget.toFixed(0)}`} />
                  <TripMetric icon={<Gauge />} value={`${trip.estimatedArrivalBatteryPercent}%`} label="Arrival battery" />
                </div>
                <div className="active-plan-breakdown">
                  <span>Base {trip.baseRouteDistanceKm} km</span>
                  <span>Detour +{trip.chargingDetourDistanceKm} km / {trip.chargingDetourMinutes}m</span>
                  <span>Charge {trip.estimatedChargingMinutes}m</span>
                  <span>Queue {trip.estimatedQueueMinutes}m</span>
                  <span>Setup {trip.connectionOverheadMinutes}m</span>
                </div>
                <div className="journey-line-scroll-wrap">
                  <div className="journey-line">
                    <div className="journey-endpoint"><span className="journey-dot start" /><strong>{trip.origin}</strong><small>{trip.telemetry.batteryPercent}% now</small></div>
                    {trip.stops.map((stop) => {
                      const isFaulted = stop.status === 'CANCELLED' || Boolean(stop.removalReason);
                      const isReplacement = stop.selectionType === 'REROUTED_REPLACEMENT'
                        || Boolean(stop.replacesStationName)
                        || (pairedReplacement && stop.id === pairedReplacement.id);
                      const isNextReserved = stop.status === 'RESERVED' && !isReplacement;

                      return (
                        <React.Fragment key={stop.id}>
                          <div className={`journey-stop ${isFaulted ? 'cancelled' : isReplacement ? 'replacement' : stop.status.toLowerCase()}`}>
                            <span className={`journey-connector ${isFaulted ? 'failed' : isReplacement ? 'replacement' : ''}`}>
                              {isFaulted ? '✕' : isReplacement ? <RefreshCw size={13} /> : <Zap size={14} />}
                            </span>
                            <strong>{stop.stationName}</strong>
                            <span className={`timeline-badge ${isFaulted ? 'badge-failed' : isReplacement ? 'badge-replacement' : isNextReserved ? 'badge-next' : ''}`}>
                              {isFaulted ? '❌ FAILED' : isReplacement ? '🔄 REPLACEMENT' : isNextReserved ? '⚡ NEXT STOP' : stop.status}
                            </span>
                            <small>{stop.arrivalBatteryPercent}% → {stop.targetBatteryPercent}%</small>
                          </div>
                          {isFaulted && (
                            <div className="journey-reroute-indicator">
                              <span>↓ REPLACED BY</span>
                            </div>
                          )}
                        </React.Fragment>
                      );
                    })}
                    <div className="journey-endpoint destination"><span className="journey-dot end" /><strong>{trip.destination}</strong><small>{trip.estimatedArrivalBatteryPercent}% reserve</small></div>
                  </div>
                </div>
              </section>

              <AutopilotJourneyMap
                origin={trip.origin}
                destination={trip.destination}
                stops={trip.stops}
                cancelledStop={cancelledStop}
                proposedReplacement={proposedReplacement}
                title="Active Autonomous Journey Route"
              />

              {trip.status === 'REPLAN_REQUIRED' && (
                <section className="autopilot-recovery replan-required" role="alert">
                  <div className="recovery-icon"><AlertTriangle size={22} /></div>
                  <div className="recovery-copy"><div className="recovery-eyebrow">SAFE STOP REQUIRED</div><h2>No compatible replacement currently fits your limits</h2><p>The unavailable reservation was released without a fee. Stop safely and adjust the route, budget, or charging constraints before continuing.</p></div>
                </section>
              )}

              <section className="autopilot-card stops-card">
                <div className="simple-card-head"><div><h2>Charging stops</h2><p>Selected for total journey impact—not simply nearest distance.</p></div><span>{trip.stops.filter((stop) => stop.status === 'PLANNED' || stop.status === 'RESERVED').length} remaining</span></div>
                <div className="stops-list">
                  {trip.stops.map((stop) => {
                    const isFaulted = stop.status === 'CANCELLED' || Boolean(stop.removalReason);
                    const isReplacement = stop.selectionType === 'REROUTED_REPLACEMENT'
                      || Boolean(stop.replacesStationName)
                      || (pairedReplacement && stop.id === pairedReplacement.id);
                    const isNextReserved = stop.status === 'RESERVED' && !isReplacement;

                    if (isFaulted) {
                      return (
                        <React.Fragment key={stop.id}>
                          <article className="stop-card stop-failed-card">
                            <div className="stop-card-flag flag-failed">
                              ❌ CHARGER FAILED · REMOVED FROM JOURNEY
                            </div>
                            <div className="stop-card-inner">
                              <div className="stop-sequence failed">✕</div>
                              <div className="stop-copy">
                                <div className="stop-title-row">
                                  <h3>{stop.stationName}</h3>
                                  <span className="original-stop-pill">Original stop #{stop.originalStopIndex || stop.sequenceNumber}</span>
                                </div>
                                <p><MapPin size={13} /> {stop.stationAddress}</p>
                                <div className="stop-failed-alert-box">
                                  <AlertTriangle size={14} />
                                  <span>Charger became unavailable during the journey. Reservation cancelled without fee.</span>
                                </div>
                              </div>
                            </div>
                          </article>
                          <div className="stop-flow-arrow">
                            <div className="arrow-line" />
                            <div className="arrow-pill">
                              <RefreshCw size={12} className="spin-slow" />
                              <span>REPLACED BY ↓</span>
                            </div>
                            <div className="arrow-line" />
                          </div>
                        </React.Fragment>
                      );
                    }

                    if (isReplacement) {
                      return (
                        <article className="stop-card stop-replacement-card" key={stop.id}>
                          <div className="stop-card-flag flag-replacement">
                            🔄 AUTO-REROUTED · REPLACEMENT STOP
                          </div>
                          <div className="stop-card-inner">
                            <div className="stop-sequence replacement">🔄</div>
                            <div className="stop-copy">
                              <div className="stop-title-row">
                                <h3>{stop.stationName}</h3>
                                <span className="replacement-source-pill">
                                  Replacement for {stop.replacesStationName || cancelledStop?.stationName || 'Dausa District Demo Hub'}
                                </span>
                                <span className="status-pill-replacement">{stop.status}</span>
                              </div>
                              <p><MapPin size={13} /> {stop.stationAddress}</p>

                              {/* Prominent Delta Metrics strip */}
                              <div className="stop-delta-callout-strip">
                                <span className="delta-item time">
                                  <Clock3 size={13} /> <strong>+{stop.additionalMinutes ?? (rerouteImpact?.delayMinutes ?? 7)} min</strong> route impact
                                </span>
                                <span className="delta-item cost">
                                  <IndianRupee size={13} /> <strong>+₹{(stop.additionalCost ?? 18.5).toFixed(0)}</strong> cost difference
                                </span>
                                <span className="delta-item charger">
                                  <Zap size={13} /> {stop.connectorType} · {stop.powerKw} kW rated
                                </span>
                              </div>

                              <div className="stop-specs">
                                <span><Clock3 size={13} /> {stop.estimatedWaitMinutes + stop.chargingMinutes} min impact</span>
                                <span><IndianRupee size={13} /> ₹{stop.estimatedCost.toFixed(0)}</span>
                              </div>
                              {stop.selectionReason && (
                                <p className="stop-selection-reason"><BrainCircuit size={12} /> {stop.selectionReason}</p>
                              )}
                            </div>
                            <div className="battery-transfer">
                              <small>ARRIVE</small>
                              <strong>{stop.arrivalBatteryPercent}%</strong>
                              <ArrowRight size={15} />
                              <small>LEAVE</small>
                              <strong>{stop.targetBatteryPercent}%</strong>
                            </div>
                          </div>
                        </article>
                      );
                    }

                    return (
                      <article className={`stop-card stop-${stop.status.toLowerCase()}`} key={stop.id}>
                        {isNextReserved && (
                          <div className="stop-card-flag flag-next">
                            ⚡ NEXT STOP · RESERVED
                          </div>
                        )}
                        <div className="stop-card-inner">
                          <div className="stop-sequence">{stop.sequenceNumber}</div>
                          <div className="stop-copy">
                            <div className="stop-title-row">
                              <h3>{stop.stationName}</h3>
                              {stop.demoData && <span className="demo-data-badge">DEMO DATA</span>}
                              <span className={isNextReserved ? 'status-pill-reserved' : ''}>{isNextReserved ? 'RESERVED' : stop.status}</span>
                            </div>
                            <p><MapPin size={13} /> {stop.stationAddress}</p>
                            <div className="stop-specs">
                              <span><Zap size={13} /> {stop.connectorType} · {stop.powerKw} kW rated{stop.effectivePowerKw > 0 ? ` · ~${stop.effectivePowerKw} kW effective` : ''}</span>
                              <span><Clock3 size={13} /> {stop.estimatedWaitMinutes + stop.chargingMinutes} min impact</span>
                              <span><IndianRupee size={13} /> ₹{stop.estimatedCost.toFixed(0)}</span>
                            </div>
                            {stop.selectionReason && <p className="stop-selection-reason"><BrainCircuit size={12} /> {stop.selectionReason}</p>}
                          </div>
                          <div className="battery-transfer">
                            <small>ARRIVE</small>
                            <strong>{stop.arrivalBatteryPercent}%</strong>
                            <ArrowRight size={15} />
                            <small>LEAVE</small>
                            <strong>{stop.targetBatteryPercent}%</strong>
                          </div>
                        </div>
                      </article>
                    );
                  })}
                </div>
              </section>
            </>
          )}
        </div>

        <aside className="autopilot-side-column">
          {trip ? (
            <>
              <section className="telemetry-card">
                <div className="telemetry-head"><div><span>LIVE VEHICLE</span><h2>{trip.telemetry.vehicleName}</h2><p>{trip.telemetry.registrationNumber} · {trip.telemetry.connectorType}</p></div><span className="live-pill"><i /> LIVE</span></div>
                <div className="battery-dial" style={{ '--battery': `${trip.telemetry.batteryPercent * 3.6}deg` } as React.CSSProperties}>
                  <div><BatteryCharging size={24} /><strong>{trip.telemetry.batteryPercent}%</strong><span>{trip.telemetry.remainingRangeKm} km range</span></div>
                </div>
                <div className="telemetry-stats"><span><small>STATE</small><strong>{trip.telemetry.state.replaceAll('_', ' ')}</strong></span><span><small>WALLET</small><strong>₹{trip.walletBalance.toFixed(0)}</strong></span></div>
                {trip.paymentMessage && <div className={`payment-note ${trip.status === 'PAYMENT_REQUIRED' ? 'warning' : ''}`}><WalletCards size={16} /><span>{trip.paymentMessage}</span></div>}
              </section>

              <section className="action-control-card">
                <div className="simple-card-head"><div><h2>Journey controls</h2><p>Update live trip progress.</p></div></div>
                <div className="control-stack">
                  {trip.status === 'RESERVED' && <ActionButton icon={<Navigation size={17} />} label="Start monitored journey" detail="Begin telemetry and live checks" busy={action === 'start'} onClick={() => void runAction('start', () => startAutopilotTrip(token, trip.id))} />}
                  {(trip.status === 'MONITORING' || trip.status === 'RESERVED') && <ActionButton icon={<AlertTriangle size={17} />} label="Simulate charger fault" detail="Reroute, rebook and alert network teams" danger busy={action === 'fault'} onClick={() => void runAction('fault', () => simulateAutopilotFault(token, trip.id))} />}
                  {trip.status === 'REROUTE_APPROVAL_REQUIRED' && <ActionButton icon={<ShieldCheck size={17} />} label="Approve replacement charger" detail={proposedReplacement ? `${proposedReplacement.stationName} · ₹${proposedReplacement.estimatedCost.toFixed(0)}` : 'Review the proposed route'} busy={action === 'approve-reroute'} onClick={() => void runAction('approve-reroute', () => approveAutopilotReroute(token, trip.id))} />}
                  {['MONITORING', 'REROUTED', 'PAYMENT_REQUIRED', 'RESERVED'].includes(trip.status) && <ActionButton icon={<Zap size={17} />} label="Complete charging + AutoPay" detail={activeStop ? `Pay ₹${activeStop.estimatedCost.toFixed(0)} from wallet` : 'Finish active session'} busy={action === 'complete'} onClick={() => void runAction('complete', () => completeAutopilotCharging(token, trip.id))} />}
                  {activeStop && <ActionButton icon={<Clock3 size={17} />} label="Report excessive wait" detail="Teach future plans on this route" busy={action === 'wait-memory'} onClick={() => void saveRouteExperience('wait-memory', 'EXCESS_WAIT', `Unexpected wait at ${activeStop.stationName}`, Math.max(15, activeStop.estimatedWaitMinutes + 15))} />}
                  {activeStop && <ActionButton icon={<AlertTriangle size={17} />} label="Report access issue" detail="Lower this stop for later drivers" danger busy={action === 'access-memory'} onClick={() => void saveRouteExperience('access-memory', 'ACCESS_ISSUE', `Driver reported an access issue at ${activeStop.stationName}`)} />}
                  {(trip.status === 'PAYMENT_REQUIRED' || trip.walletBalance < (activeStop?.estimatedCost ?? 0)) && <ActionButton icon={<IndianRupee size={17} />} label="Top up ₹1,000" detail="Simulated UPI funding" busy={action === 'topup'} onClick={() => void topUp()} />}
                  <button className="wallet-link" onClick={onOpenWallet}><WalletCards size={15} /> Open wallet & auto-recharge</button>
                </div>
              </section>

              <section className="timeline-card">
                <div className="timeline-head"><div><div className="autopilot-kicker"><Bot size={13} /> ACTION TIMELINE</div><h2>Vidyut is doing the work</h2></div><button onClick={() => void refresh()} title="Refresh"><RefreshCw size={16} /></button></div>
                <div className="timeline-list">
                  {[...trip.timeline].reverse().map((item) => (
                    <div className={`timeline-item ${item.state.toLowerCase()}`} key={item.sequenceNumber}>
                      <div className="timeline-marker">{item.state === 'WARNING' ? <AlertTriangle size={13} /> : item.state === 'INFO' ? <Bot size={13} /> : <Check size={13} />}</div>
                      <div><div className="timeline-title"><strong>{item.title}</strong><time>{new Date(item.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time></div><p>{item.detail}</p></div>
                    </div>
                  ))}
                </div>
              </section>
            </>
          ) : (
            <section className="autopilot-empty-state">
              <div><Bot size={32} /></div>
              <h2>No active journey</h2>
              <p>Connect an EV and give Vidyut a destination. The action timeline will prove every autonomous decision here.</p>
              <ul><li><Check size={14} /> Connector compatibility</li><li><Check size={14} /> Queue and charging time</li><li><Check size={14} /> Safe arrival reserve</li><li><Check size={14} /> Booking and AutoPay</li></ul>
            </section>
          )}
        </aside>
      </div>
    </div>
  );
}

function ModeButton({
  active,
  recommended,
  icon,
  title,
  emoji,
  detail,
  onClick,
}: {
  active: boolean;
  recommended?: boolean;
  icon: React.ReactNode;
  title: string;
  emoji?: string;
  detail: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={active}
      className={`autonomy-mode-option ${active ? 'active' : ''}`}
      onClick={onClick}
    >
      <span className="mode-icon">{icon}</span>
      <span className="mode-text-wrap">
        <span className="mode-title-row">
          <strong>{title}</strong>
          {emoji && <span className="mode-emoji">{emoji}</span>}
        </span>
        <small>{detail}</small>
      </span>
      {recommended && <em className="rec-badge">RECOMMENDED</em>}
      <i>{active && <Check size={12} />}</i>
    </button>
  );
}

function VehicleRecommendationPanel({
  recommendation,
  onChoose,
}: {
  recommendation: VehicleRecommendation;
  onChoose: (vehicleId: number) => void;
}) {
  const recommended = recommendation.vehicles.find(
    (vehicle) => vehicle.vehicleId === recommendation.recommendedVehicleId,
  );
  const others = recommendation.vehicles.filter(
    (vehicle) => vehicle.vehicleId !== recommendation.recommendedVehicleId,
  );

  return (
    <section className="autopilot-card vehicle-recommendation-card" aria-live="polite">
      <div className="vehicle-recommendation-heading">
        <div className="vehicle-recommendation-icon"><CarFront size={22} /></div>
        <div>
          <span><Sparkles size={12} /> GARAGE COMPARISON</span>
          <h2>Recommended vehicle for this trip</h2>
          <p>{recommendation.origin} → {recommendation.destination} · ranked for {optimizationLabel(recommendation.optimizeFor)}</p>
        </div>
      </div>

      {recommended ? (
        <article className="recommended-vehicle-result">
          <div className="recommended-vehicle-copy">
            <div><em>BEST MATCH</em><span>{recommended.registrationNumber}</span></div>
            <h3>{recommended.vehicleName}</h3>
            <p>{recommendation.reason}</p>
            <div className="recommended-vehicle-specs">
              <span><Zap size={13} /> {recommended.supportedConnectors.map(connectorName).join(' + ')}</span>
              <span><BatteryCharging size={13} /> {recommended.batteryCapacityKwh} kWh · {recommended.currentBatteryPercent}%</span>
              <span><Gauge size={13} /> {recommended.efficiencyWhPerKm} Wh/km</span>
            </div>
          </div>
          <div className="recommended-vehicle-metrics">
            <span><strong>{recommended.chargingStops}</strong><small>charging stops</small></span>
            <span><strong>{formatMinutes(recommended.journeyMinutes)}</strong><small>door-to-door</small></span>
            <span><strong>₹{recommended.estimatedCost.toFixed(0)}</strong><small>charging</small></span>
            <span><strong>{recommended.arrivalBatteryPercent}%</strong><small>arrival battery</small></span>
          </div>
        </article>
      ) : (
        <div className="vehicle-recommendation-empty" role="status">
          <AlertTriangle size={20} />
          <div><strong>No saved car satisfies every constraint</strong><p>{recommendation.reason}</p></div>
        </div>
      )}

      {others.length > 0 && (
        <div className="vehicle-comparison-list">
          <div className="vehicle-comparison-title"><h3>Other vehicles</h3><span>{others.length} compared</span></div>
          {others.map((vehicle) => (
            <article key={vehicle.vehicleId} className={vehicle.feasible ? 'feasible' : 'infeasible'}>
              <div className="vehicle-comparison-status">
                {vehicle.feasible ? <CircleCheckBig size={16} /> : <AlertTriangle size={16} />}
              </div>
              <div className="vehicle-comparison-copy">
                <div><strong>{vehicle.vehicleName}</strong><span>{vehicle.supportedConnectors.map(connectorName).join(' + ')}</span></div>
                <p>{vehicle.reason}</p>
              </div>
              {vehicle.feasible ? (
                <div className="vehicle-comparison-numbers">
                  <span>{vehicle.chargingStops} stop{vehicle.chargingStops === 1 ? '' : 's'}</span>
                  <span>{formatMinutes(vehicle.journeyMinutes)}</span>
                  <span>₹{vehicle.estimatedCost.toFixed(0)}</span>
                  <button type="button" onClick={() => onChoose(vehicle.vehicleId)}>Use this car</button>
                </div>
              ) : <span className="vehicle-not-feasible">NOT FEASIBLE</span>}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function AutopilotProposal({
  plan,
  mode,
  reply,
  toolCalls,
  busy,
  onConfirm,
  onUseRequiredBudget,
}: {
  plan: AutopilotPlan;
  mode: AutopilotMode;
  reply: string;
  toolCalls: Array<{ name: string; status: string }>;
  busy: boolean;
  onConfirm: () => void;
  onUseRequiredBudget: () => void;
}) {
  const feasibilityCopy = plan.overallFeasible
    ? { heading: 'A route that fits your limits', label: 'Feasible' }
    : !plan.deadlineFeasible
      ? { heading: 'Safe route found — arrival deadline missed', label: 'Deadline missed' }
      : !plan.withinBudget
        ? { heading: 'Route ready — review the budget', label: 'Budget update needed' }
        : { heading: 'Route ready — review the safety limits', label: 'Constraint update needed' };
  const actionCopy = mode === 'FULL_AUTOPILOT'
    ? {
        title: 'Launch Full Autopilot',
        detail: 'Vidyut may book and reroute automatically inside these limits.',
      }
    : {
        title: `Confirm & reserve ${plan.stops.length} stop${plan.stops.length === 1 ? '' : 's'}`,
        detail: `Estimated authorization ₹${plan.estimatedChargingCost.toFixed(0)} · no charge is made now.`,
      };

  return (
    <section className="autopilot-card agent-proposal-card" aria-live="polite">
      <div className="proposal-header">
        <div className="proposal-agent-mark"><Bot size={22} /></div>
        <div className="proposal-title-copy">
          <div className="proposal-eyebrow"><Sparkles size={12} /> VIDYUT PLAN READY</div>
          <h2>{feasibilityCopy.heading}</h2>
          <p>{plan.vehicleName} · {plan.registrationNumber} · {plan.connectorType}</p>
        </div>
        <div className={`proposal-confidence ${plan.overallFeasible ? '' : 'warning'}`}>
          {plan.overallFeasible ? <CircleCheckBig size={16} /> : <AlertTriangle size={16} />}
          <span><strong>{feasibilityCopy.label}</strong><small>{plan.compatibleChargersEvaluated} chargers checked</small></span>
        </div>
      </div>

      <div className="proposal-route-band">
        <div><small>START</small><strong>{plan.origin}</strong><span>{plan.currentBatteryPercent}% battery</span></div>
        <div className="proposal-route-line"><i /><Route size={18} /><i /></div>
        <div className="destination"><small>ARRIVE</small><strong>{plan.destination}</strong><span>~{plan.estimatedArrivalTime} · {plan.estimatedArrivalBatteryPercent}%</span></div>
      </div>

      <div className="proposal-metrics">
        <TripMetric icon={<Route />} value={`${plan.totalDistanceKm} km`} label="Journey distance" />
        <TripMetric icon={<Clock3 />} value={formatMinutes(plan.totalDurationMinutes)} label={`ETA ${plan.estimatedArrivalTime}`} />
        <TripMetric
          icon={<IndianRupee />}
          value={`₹${plan.estimatedChargingCost.toFixed(0)}`}
          label={plan.withinBudget
            ? `₹${Math.max(0, plan.budgetRemaining).toFixed(0)} budget left`
            : `₹${Math.abs(plan.budgetRemaining).toFixed(0)} over budget`}
        />
        <TripMetric icon={<ShieldCheck />} value={`${plan.estimatedArrivalBatteryPercent}%`} label={`${plan.minimumArrivalBatteryPercent}% minimum`} />
      </div>

      <div className="proposal-audit-grid">
        <article>
          <div className="proposal-audit-title"><Route size={15} /><h3>Route evidence</h3></div>
          <dl>
            <div><dt>Base road route</dt><dd>{plan.baseRouteDistanceKm} km</dd></div>
            <div><dt>Charging detour</dt><dd>+{plan.chargingDetourDistanceKm} km</dd></div>
            <div className="total"><dt>Final EV route</dt><dd>{plan.totalDistanceKm} km</dd></div>
          </dl>
          <small>{plan.routeEngine.replaceAll('_', ' ').toLowerCase()}</small>
        </article>
        <article>
          <div className="proposal-audit-title"><Clock3 size={15} /><h3>Time accounting</h3></div>
          <dl>
            <div><dt>Base driving</dt><dd>{plan.baseDriveMinutes}m</dd></div>
            <div><dt>Charger detours</dt><dd>{plan.chargingDetourMinutes}m</dd></div>
            <div><dt>Charging</dt><dd>{plan.estimatedChargingMinutes}m</dd></div>
            <div><dt>Queue</dt><dd>{plan.estimatedQueueMinutes}m</dd></div>
            <div><dt>Plug / setup</dt><dd>{plan.connectionOverheadMinutes}m</dd></div>
            <div className="total"><dt>Door-to-door</dt><dd>{plan.totalDurationMinutes}m</dd></div>
          </dl>
        </article>
        <article>
          <div className="proposal-audit-title"><BatteryCharging size={15} /><h3>Vehicle energy model</h3></div>
          <dl>
            <div><dt>Battery capacity</dt><dd>{plan.batteryCapacityKwh} kWh</dd></div>
            <div><dt>Usable before reserve</dt><dd>{plan.availableEnergyKwh} kWh</dd></div>
            <div><dt>Consumption</dt><dd>{plan.energyConsumptionKwhPer100Km} kWh/100 km</dd></div>
            <div><dt>Maximum DC input</dt><dd>{plan.vehicleMaxChargingPowerKw} kW</dd></div>
            <div><dt>Battery-side efficiency</dt><dd>{plan.chargingEfficiencyPercent}%</dd></div>
            <div><dt>Start / reserve</dt><dd>{plan.currentBatteryPercent}% / {plan.minimumArrivalBatteryPercent}%</dd></div>
            <div className="total"><dt>Expected arrival</dt><dd>{plan.estimatedArrivalBatteryPercent}%</dd></div>
          </dl>
        </article>
      </div>

      <div className="proposal-validation-row">
        <span className={plan.safeArrivalReserve ? 'passed' : 'failed'}><ShieldCheck size={13} /> Safe reserve</span>
        <span className={plan.withinBudget ? 'passed' : 'failed'}>
          <WalletCards size={13} /> {plan.withinBudget ? 'Within budget' : 'Budget exceeded'}
        </span>
        <span className={plan.deadlineFeasible ? 'passed' : 'failed'}>
          <Clock3 size={13} /> {plan.arrivalDeadline
            ? plan.deadlineFeasible ? 'Deadline met' : 'Arrival deadline missed'
            : 'No arrival deadline'}
        </span>
        <span className={plan.liveAvailabilityChecked ? 'passed' : 'failed'}><Wifi size={13} /> Live availability</span>
        <span className="passed"><Zap size={13} /> Connector matched</span>
      </div>

      {!plan.withinBudget && (
        <div className="proposal-budget-warning" role="status">
          <AlertTriangle size={18} />
          <div>
            <strong>The road route is safe, but the current limit is too low.</strong>
            <p>Estimated charging is ₹{plan.estimatedChargingCost.toFixed(0)}. No booking or payment has been made.</p>
          </div>
          <button type="button" onClick={onUseRequiredBudget}>
            Use ₹{Math.ceil(plan.estimatedChargingCost)} budget
          </button>
        </div>
      )}

      {!plan.deadlineFeasible && plan.arrivalDeadline && (
        <div className="proposal-deadline-warning" role="status">
          <AlertTriangle size={18} />
          <div>
            <strong>{plan.safeArrivalReserve && plan.withinBudget
              ? 'Battery and budget are feasible, but the requested arrival is not.'
              : 'The requested arrival deadline is also not feasible.'}</strong>
            <p>
              Expected arrival: {formatClockTime(plan.estimatedArrivalTime)} · Requested arrival:{' '}
              {formatClockTime(plan.arrivalDeadline)} · Late by: {formatMinutes(plan.deadlineMinutesLate)}.
              No booking or payment has been made.
            </p>
          </div>
        </div>
      )}

      <div className="proposal-purpose-memory">
        <div><Navigation size={15} /><span><strong>{plan.tripPurpose.replaceAll('_', ' ')}</strong><small>{plan.purposeSummary}</small></span></div>
        <div><BrainCircuit size={15} /><span><strong>{plan.pastExperiencesUsed} past route signals</strong><small>{plan.memorySummary}</small></span></div>
      </div>

      <AutopilotJourneyMap
        origin={plan.origin}
        destination={plan.destination}
        stops={plan.stops}
        title="Proposed Autonomous Journey Corridor"
      />

      <div className="proposal-stops-heading">
        <div><h3>Recommended charging plan</h3><p>Optimized for {plan.optimizeFor.toLowerCase()} across {plan.feasibleAlternativesCompared} feasible energy states.</p></div>
        <span>{plan.stops.length} STOP{plan.stops.length === 1 ? '' : 'S'}</span>
      </div>
      <div className="proposal-stops">
        {plan.stops.map((stop) => (
          <article className="proposal-stop" key={`${stop.stationId}-${stop.sequenceNumber}`}>
            <div className="proposal-stop-index">{stop.sequenceNumber}</div>
            <div className="proposal-stop-main">
              <div className="proposal-stop-title">
                <div><div className="proposal-stop-name"><h4>{stop.stationName}</h4>{stop.demoData && <em>DEMO DATA</em>}</div><p><MapPin size={12} /> {stop.stationAddress}</p></div>
                <span><Star size={11} /> {stop.rating.toFixed(1)}</span>
              </div>
              <div className="proposal-stop-details">
                <span><Clock3 size={12} /> ETA {stop.estimatedArrivalTime}</span>
                <span><Zap size={12} /> {stop.connectorType} · {stop.powerKw} kW rated · ~{stop.effectivePowerKw} kW to battery</span>
                <span><Wifi size={12} /> {stop.availableConnectors} live</span>
                <span><IndianRupee size={12} /> ₹{stop.estimatedCost.toFixed(0)}</span>
              </div>
              {stop.selectionReason && <p className="proposal-stop-reason"><Sparkles size={11} /> {stop.selectionReason}</p>}
            </div>
            <div className="proposal-charge-block">
              <small>{stop.estimatedWaitMinutes}m wait + {stop.chargingMinutes}m charge + {stop.connectionMinutes}m setup</small>
              <small>{stop.routeOffsetKm} km from base route</small>
              <div><strong>{stop.arrivalBatteryPercent}%</strong><ArrowRight size={14} /><strong>{stop.targetBatteryPercent}%</strong></div>
            </div>
          </article>
        ))}
      </div>

      <div className="proposal-agent-explanation">
        <span><Bot size={17} /></span>
        <div><strong>Why Vidyut chose this plan</strong><p>{plan.optimizationSummary}</p><p>{compactAgentReply(reply) || (plan.overallFeasible
          ? `The route engine compared compatible live chargers and kept the journey within your ₹${plan.maximumChargingBudget.toFixed(0)} limit and ${plan.minimumArrivalBatteryPercent}% reserve.`
          : !plan.deadlineFeasible
            ? `The route is safe for battery and budget, but it arrives ${formatMinutes(plan.deadlineMinutesLate)} after your requested deadline.`
            : `The route engine found a battery-safe charger plan, but its ₹${plan.estimatedChargingCost.toFixed(0)} estimate is above your ₹${plan.maximumChargingBudget.toFixed(0)} limit.`)}</p></div>
      </div>

      {toolCalls.length > 0 && (
        <div className="proposal-evidence">
          <span>Verified with</span>
          {toolCalls.map((tool) => <i key={tool.name}><Check size={10} /> {toolLabel(tool.name)}</i>)}
        </div>
      )}

      <div className={`proposal-consent mode-${mode.toLowerCase()}`}>
        {mode === 'RECOMMEND_ONLY' ? (
          <>
            <div className="consent-icon"><Eye size={19} /></div>
            <div><strong>Recommendation only</strong><p>No bookings or payments were made. You stay in control of every action.</p></div>
            <span className="no-action-badge">NO ACTION TAKEN</span>
          </>
        ) : (
          <>
            <div className="consent-icon">{mode === 'FULL_AUTOPILOT' ? <RadioTower size={19} /> : <ShieldCheck size={19} />}</div>
            <div><strong>{actionCopy.title}</strong><p>{actionCopy.detail}</p></div>
            <button type="button" onClick={onConfirm} disabled={busy || !plan.overallFeasible}>
              {busy ? <LoaderCircle className="spin" size={17} /> : <Check size={17} />}
              {busy ? 'Authorizing…' : mode === 'FULL_AUTOPILOT' ? 'Launch Autopilot' : 'Confirm Autopilot'}
            </button>
          </>
        )}
      </div>
    </section>
  );
}

function TripMetric({ icon, value, label }: { icon: React.ReactNode; value: string; label: string }) {
  return <div className="trip-metric"><span>{icon}</span><div><strong>{value}</strong><small>{label}</small></div></div>;
}

function toolLabel(name: string): string {
  return ({
    get_vehicle_status: 'Vehicle telemetry',
    preview_autopilot_trip: 'Route & charger planner',
    launch_autopilot_trip: 'Reservation engine',
  } as Record<string, string>)[name] ?? name.replaceAll('_', ' ');
}

function ActionButton({ icon, label, detail, busy, danger, onClick }: { icon: React.ReactNode; label: string; detail: string; busy: boolean; danger?: boolean; onClick: () => void }) {
  return <button className={`action-control ${danger ? 'danger' : ''}`} onClick={onClick} disabled={busy}><span>{busy ? <LoaderCircle className="spin" size={17} /> : icon}</span><div><strong>{label}</strong><small>{detail}</small></div><ArrowRight size={15} /></button>;
}

function formatMinutes(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return hours ? `${hours}h ${remainder}m` : `${remainder}m`;
}

function connectorName(value: string): string {
  if (value === 'GB_T') return 'GB/T';
  if (value === 'TYPE1') return 'Type 1';
  if (value === 'TYPE2') return 'Type 2';
  return value;
}

function optimizationLabel(value: VehicleRecommendation['optimizeFor']): string {
  return value === 'COST' ? 'lowest cost' : value === 'BALANCED' ? 'balanced' : 'fastest journey';
}

function formatClockTime(value: string): string {
  const [hourText, minuteText] = value.split(':');
  const hour = Number(hourText);
  if (!Number.isFinite(hour) || minuteText == null) return value;
  const period = hour >= 12 ? 'PM' : 'AM';
  const displayHour = hour % 12 || 12;
  return `${displayHour}:${minuteText} ${period}`;
}

function compactAgentReply(reply: string): string {
  if (!reply.trim()) return '';
  const paragraph = reply
    .split(/\r?\n\s*\r?\n/)
    .map((value) => value.trim())
    .find((value) => value && !value.startsWith('#')) ?? reply.trim();
  const plain = paragraph
    .replace(/^#{1,6}\s+/g, '')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/[`*_]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  return plain.length > 420 ? `${plain.slice(0, 417).trimEnd()}…` : plain;
}

function statusLabel(status: AutopilotTrip['status']): string {
  return ({ RESERVED: 'Charger reserved', MONITORING: 'Monitoring live', REROUTED: 'Route updated', REROUTE_APPROVAL_REQUIRED: 'Approve reroute', REPLAN_REQUIRED: 'Safe replan needed', PAYMENT_REQUIRED: 'Payment needed', COMPLETED: 'Autopilot complete', CANCELLED: 'Cancelled' })[status];
}

function messageFor(error: unknown): string {
  return error instanceof Error ? error.message : 'The Autopilot action could not be completed.';
}
