import { useEffect, useState } from 'react';
import { BatteryCharging, CarFront, ExternalLink, MapPin, Navigation, Route, ShieldCheck, Zap } from 'lucide-react';
import { apiRequest } from '../services/api';
import { getVehicles, type Vehicle } from '../services/vehicles';
import './TripPlannerView.css';

interface RouteStop {
  station: { id: number; name: string; address: string; pricePerKwh: number; availableSlots: number; connectors: Array<{ type: string; powerKw: number }> };
  distanceFromOriginKm: number;
  detourKm: number;
  etaMinutes: number;
  availableSlots: number;
  recommendedChargeMinutes: number;
  estimatedChargingCost: number;
  reason: string;
}

interface RoutePlan {
  origin: string;
  destination: string;
  totalDistanceKm: number;
  totalDurationMinutes: number;
  usableRangeKm: number;
  reserveBatteryPercent: number;
  estimatedArrivalBatteryPercent: number;
  destinationWithinRange: boolean;
  externalMapsUrl: string;
  recommendedChargingStops: RouteStop[];
}

export function TripPlannerView({ token }: { token: string }) {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [vehicleId, setVehicleId] = useState(0);
  const [origin, setOrigin] = useState('Lucknow');
  const [destination, setDestination] = useState('Kanpur');
  const [battery, setBattery] = useState(70);
  const [plan, setPlan] = useState<RoutePlan | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    void getVehicles(token).then((items) => {
      setVehicles(items);
      setVehicleId((current) => current || items[0]?.id || 0);
      const first = items[0];
      if (first?.batteryPercent != null) setBattery(first.batteryPercent);
    }).catch((reason) => setError(reason instanceof Error ? reason.message : 'Unable to load vehicles.'));
  }, [token]);

  const submit = async () => {
    if (!vehicleId || !origin.trim() || !destination.trim()) {
      setError('Choose a vehicle and enter both locations.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const response = await apiRequest<RoutePlan>('/routing/plan', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: JSON.stringify({ vehicleId, origin, destination, currentBatteryPercent: battery, reserveBatteryPercent: 10 }),
      });
      setPlan(response);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to plan this trip.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="trip-page" aria-labelledby="trip-title">
      <header className="trip-heading">
        <div><span>RANGE-AWARE ROUTING</span><h1 id="trip-title">Plan your EV trip</h1><p>Compatible stops ranked by reserve range, live slots, detour and price—without agent automation.</p></div>
        <div className="trip-safety"><ShieldCheck size={17} /> Deterministic route</div>
      </header>

      <div className="trip-layout">
        <form className="trip-form" onSubmit={(event) => { event.preventDefault(); void submit(); }}>
          <label>Vehicle<select value={vehicleId} onChange={(event) => setVehicleId(Number(event.target.value))}><option value={0}>Choose vehicle</option>{vehicles.map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.makeAndModel} • {vehicle.connectorType || 'connector unknown'}</option>)}</select></label>
          <div className="trip-location"><MapPin size={18} /><label>Starting from<input value={origin} onChange={(event) => setOrigin(event.target.value)} placeholder="Lucknow" /></label></div>
          <div className="trip-line" />
          <div className="trip-location destination"><Navigation size={18} /><label>Destination<input value={destination} onChange={(event) => setDestination(event.target.value)} placeholder="Kanpur" /></label></div>
          <label>Current battery <strong>{battery}%</strong><input type="range" min="10" max="100" step="5" value={battery} onChange={(event) => setBattery(Number(event.target.value))} /></label>
          {error && <div className="trip-error">{error}</div>}
          <button type="submit" className="trip-submit" disabled={loading || !vehicles.length}><Route size={17} />{loading ? 'Planning route…' : 'Plan route'}</button>
        </form>

        <div className="trip-results">
          {!plan ? <div className="trip-empty"><Route size={34} /><h2>Ready when you are</h2><p>Enter a destination to check whether the current battery is enough and where to charge.</p></div> : <>
            <article className={`trip-summary ${plan.destinationWithinRange ? 'direct' : 'stops'}`}>
              <div><small>{plan.destinationWithinRange ? 'DIRECT TRIP' : 'CHARGING STOP NEEDED'}</small><h2>{Math.round(plan.totalDistanceKm)} km • {Math.floor(plan.totalDurationMinutes / 60)}h {plan.totalDurationMinutes % 60}m</h2><p>Usable range {Math.round(plan.usableRangeKm)} km • arrive near {Math.round(plan.estimatedArrivalBatteryPercent)}%</p></div>
              {plan.destinationWithinRange ? <Navigation size={30} /> : <BatteryCharging size={30} />}
            </article>
            <div className="trip-route-line"><span>{origin}</span><i /><span>{destination}</span></div>
            <h3>Compatible charging stops</h3>
            {plan.recommendedChargingStops.map((stop, index) => <article className="trip-stop" key={stop.station.id}>
              <b>{index + 1}</b><div><h4>{stop.station.name}</h4><p>{stop.station.address}</p><span><Zap size={13} /> {stop.recommendedChargeMinutes} min • {stop.availableSlots} free • {stop.detourKm} km detour • est. ₹{Math.round(stop.estimatedChargingCost)}</span></div>
            </article>)}
            {!plan.recommendedChargingStops.length && <div className="trip-direct"><CarFront size={19} /> No charging stop is required inside the 10% reserve.</div>}
            <a className="trip-maps" href={plan.externalMapsUrl} target="_blank" rel="noreferrer">Open turn-by-turn directions <ExternalLink size={15} /></a>
          </>}
        </div>
      </div>
    </section>
  );
}
