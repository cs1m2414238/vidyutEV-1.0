import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowRight,
  BatteryCharging,
  Bot,
  CarFront,
  Check,
  IndianRupee,
  Clock3,
  Gauge,
  LoaderCircle,
  MapPin,
  Navigation,
  RefreshCw,
  Route,
  ShieldCheck,
  Sparkles,
  WalletCards,
  Zap,
} from 'lucide-react';
import {
  addAutopilotVehicle,
  completeAutopilotCharging,
  getAutopilotVehicles,
  getCurrentAutopilotTrip,
  launchAutopilotTrip,
  sendAutopilotAgentMessage,
  simulateAutopilotFault,
  startAutopilotTrip,
  topUpAutopilotWallet,
} from '../services/autopilot';
import type { AutopilotTrip, AutopilotTripRequest, AutopilotVehicle } from '../services/autopilot';
import './AutopilotView.css';

interface AutopilotViewProps {
  token: string;
  userName: string;
  onOpenWallet: () => void;
}

const initialGoal = "Get me from Kanpur to Delhi by 6 PM. Keep charging under ₹900 and don't let my battery fall below 15%.";

export function AutopilotView({ token, userName, onOpenWallet }: AutopilotViewProps) {
  const [trip, setTrip] = useState<AutopilotTrip | null>(null);
  const [vehicles, setVehicles] = useState<AutopilotVehicle[]>([]);
  const [vehicleId, setVehicleId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [action, setAction] = useState('');
  const [error, setError] = useState('');
  const [agentReply, setAgentReply] = useState('');
  const [agentSessionId, setAgentSessionId] = useState<string>();
  const [goal, setGoal] = useState(initialGoal);
  const [origin, setOrigin] = useState('Kanpur');
  const [destination, setDestination] = useState('Delhi');
  const [deadline, setDeadline] = useState('18:00');
  const [battery, setBattery] = useState(42);
  const [minimumBattery, setMinimumBattery] = useState(15);
  const [budget, setBudget] = useState(900);
  const [optimizeFor, setOptimizeFor] = useState<AutopilotTripRequest['optimizeFor']>('TIME');
  const [vehicleForm, setVehicleForm] = useState({
    makeAndModel: 'Tata Nexon EV',
    registrationNumber: '',
    batteryCapacity: '40.5 kWh',
    connectorType: 'CCS2',
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
      setVehicleId((current) => current ?? availableVehicles[0]?.id ?? null);
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

  const launch = async () => {
    if (!vehicleId) {
      setError('Add an EV before starting Autopilot.');
      return;
    }
    const idempotencyKey = typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `TRIP-${Date.now()}`;
    await runAction('launch', () => launchAutopilotTrip(token, {
      vehicleId,
      origin,
      destination,
      goal,
      arrivalDeadline: deadline,
      optimizeFor,
      currentBatteryPercent: battery,
      minimumArrivalBatteryPercent: minimumBattery,
      maximumChargingBudget: budget,
      idempotencyKey,
    }));
  };

  const planWithGemini = async () => {
    if (!vehicleId) {
      setError('Add an EV before asking Gemini to plan the journey.');
      return;
    }
    setAction('agent');
    setError('');
    setAgentReply('');
    try {
      const response = await sendAutopilotAgentMessage(
        token,
        `Plan and reserve a real Vidyut Autopilot trip for vehicle ID ${vehicleId}. `
          + `Travel from ${origin} to ${destination}, current battery ${battery}%, `
          + `arrive by ${deadline}, keep at least ${minimumBattery}% battery, `
          + `spend at most ₹${budget}, optimize for ${optimizeFor}. User goal: ${goal}`,
        agentSessionId,
      );
      setAgentSessionId(response.sessionId);
      setAgentReply(response.reply);
      setTrip(await getCurrentAutopilotTrip(token));
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setAction('');
    }
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

      {error && <div className="autopilot-error" role="alert"><AlertTriangle size={18} /> {error}</div>}

      <div className="autopilot-layout">
        <div className="autopilot-main-column">
          <section className="autopilot-card goal-card">
            <div className="autopilot-card-heading">
              <div>
                <span className="step-number">01</span>
                <div><h2>Set the journey goal</h2><p>Natural-language intent plus enforceable safety constraints.</p></div>
              </div>
              {vehicles.length > 0 && (
                <select value={vehicleId ?? ''} onChange={(event) => setVehicleId(Number(event.target.value))}>
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
                  <label>Connector<select value={vehicleForm.connectorType} onChange={(event) => setVehicleForm((current) => ({ ...current, connectorType: event.target.value }))}><option>CCS2</option><option>TYPE2</option><option>CHADEMO</option></select></label>
                </div>
                <button className="autopilot-secondary-button" onClick={() => void addVehicle()} disabled={Boolean(action)}>
                  {action === 'vehicle' ? <LoaderCircle className="spin" size={16} /> : <CarFront size={16} />} Add EV
                </button>
              </div>
            ) : (
              <>
                <label className="goal-prompt-label">
                  <span><Bot size={16} /> Tell Vidyut what matters</span>
                  <textarea value={goal} onChange={(event) => setGoal(event.target.value)} rows={3} />
                </label>
                <div className="route-input-row">
                  <label><span>From</span><div><MapPin size={16} /><input value={origin} onChange={(event) => setOrigin(event.target.value)} /></div></label>
                  <ArrowRight size={18} className="route-arrow" />
                  <label><span>To</span><div><Navigation size={16} /><input value={destination} onChange={(event) => setDestination(event.target.value)} /></div></label>
                </div>
                <div className="constraint-grid">
                  <label><span>Current battery</span><div><BatteryCharging size={16} /><input type="number" min="1" max="100" value={battery} onChange={(event) => setBattery(Number(event.target.value))} /><strong>%</strong></div></label>
                  <label><span>Safety reserve</span><div><ShieldCheck size={16} /><input type="number" min="5" max="50" value={minimumBattery} onChange={(event) => setMinimumBattery(Number(event.target.value))} /><strong>%</strong></div></label>
                  <label><span>Maximum budget</span><div><IndianRupee size={16} /><input type="number" min="1" value={budget} onChange={(event) => setBudget(Number(event.target.value))} /></div></label>
                  <label><span>Arrive by</span><div><Clock3 size={16} /><input type="time" value={deadline} onChange={(event) => setDeadline(event.target.value)} /></div></label>
                </div>
                <div className="goal-footer">
                  <div className="optimization-switch" aria-label="Optimization preference">
                    {(['TIME', 'BALANCED', 'COST'] as const).map((option) => (
                      <button key={option} className={optimizeFor === option ? 'active' : ''} onClick={() => setOptimizeFor(option)}>{option === 'TIME' ? 'Fastest' : option === 'COST' ? 'Lowest cost' : 'Balanced'}</button>
                    ))}
                  </div>
                  <div className="agent-plan-actions">
                    <button className="autopilot-agent-button" onClick={() => void planWithGemini()} disabled={Boolean(action)}>
                      {action === 'agent' ? <LoaderCircle className="spin" size={18} /> : <Bot size={18} />}
                      Plan with Gemini
                    </button>
                    <button className="autopilot-primary-button" onClick={() => void launch()} disabled={Boolean(action)}>
                      {action === 'launch' ? <LoaderCircle className="spin" size={18} /> : <Sparkles size={18} />}
                      Java fallback
                    </button>
                  </div>
                </div>
                {agentReply && <div className="autopilot-agent-reply"><Bot size={18} /><div><strong>Gemini agent</strong><p>{agentReply}</p></div></div>}
              </>
            )}
          </section>

          {trip && (
            <>
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
                <div className="journey-line">
                  <div className="journey-endpoint"><span className="journey-dot start" /><strong>{trip.origin}</strong><small>{trip.telemetry.batteryPercent}% now</small></div>
                  {trip.stops.map((stop) => (
                    <div className={`journey-stop ${stop.status.toLowerCase()}`} key={stop.id}>
                      <span className="journey-connector"><Zap size={14} /></span>
                      <strong>{stop.stationName}</strong>
                      <small>{stop.arrivalBatteryPercent}% → {stop.targetBatteryPercent}%</small>
                    </div>
                  ))}
                  <div className="journey-endpoint destination"><span className="journey-dot end" /><strong>{trip.destination}</strong><small>{trip.estimatedArrivalBatteryPercent}% reserve</small></div>
                </div>
              </section>

              <section className="autopilot-card stops-card">
                <div className="simple-card-head"><div><h2>Charging stops</h2><p>Selected for total journey impact—not simply nearest distance.</p></div><span>{trip.stops.filter((stop) => stop.status !== 'CANCELLED').length} active</span></div>
                <div className="stops-list">
                  {trip.stops.map((stop) => (
                    <article className={`stop-card stop-${stop.status.toLowerCase()}`} key={stop.id}>
                      <div className="stop-sequence">{stop.status === 'CANCELLED' ? <AlertTriangle size={17} /> : stop.sequenceNumber}</div>
                      <div className="stop-copy"><div className="stop-title-row"><h3>{stop.stationName}</h3><span>{stop.status}</span></div><p><MapPin size={13} /> {stop.stationAddress}</p><div className="stop-specs"><span><Zap size={13} /> {stop.connectorType} · {stop.powerKw} kW</span><span><Clock3 size={13} /> {stop.estimatedWaitMinutes + stop.chargingMinutes} min impact</span><span><IndianRupee size={13} /> ₹{stop.estimatedCost.toFixed(0)}</span></div></div>
                      <div className="battery-transfer"><small>ARRIVE</small><strong>{stop.arrivalBatteryPercent}%</strong><ArrowRight size={15} /><small>LEAVE</small><strong>{stop.targetBatteryPercent}%</strong></div>
                    </article>
                  ))}
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
                <div className="simple-card-head"><div><h2>Demo controls</h2><p>Trigger real backend actions.</p></div></div>
                <div className="control-stack">
                  {trip.status === 'RESERVED' && <ActionButton icon={<Navigation size={17} />} label="Start monitored journey" detail="Begin telemetry and live checks" busy={action === 'start'} onClick={() => void runAction('start', () => startAutopilotTrip(token, trip.id))} />}
                  {(trip.status === 'MONITORING' || trip.status === 'RESERVED') && <ActionButton icon={<AlertTriangle size={17} />} label="Simulate charger fault" detail="Cancel, replan and rebook" danger busy={action === 'fault'} onClick={() => void runAction('fault', () => simulateAutopilotFault(token, trip.id))} />}
                  {['MONITORING', 'REROUTED', 'PAYMENT_REQUIRED', 'RESERVED'].includes(trip.status) && <ActionButton icon={<Zap size={17} />} label="Complete charging + AutoPay" detail={activeStop ? `Pay ₹${activeStop.estimatedCost.toFixed(0)} from wallet` : 'Finish active session'} busy={action === 'complete'} onClick={() => void runAction('complete', () => completeAutopilotCharging(token, trip.id))} />}
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

function TripMetric({ icon, value, label }: { icon: React.ReactNode; value: string; label: string }) {
  return <div className="trip-metric"><span>{icon}</span><div><strong>{value}</strong><small>{label}</small></div></div>;
}

function ActionButton({ icon, label, detail, busy, danger, onClick }: { icon: React.ReactNode; label: string; detail: string; busy: boolean; danger?: boolean; onClick: () => void }) {
  return <button className={`action-control ${danger ? 'danger' : ''}`} onClick={onClick} disabled={busy}><span>{busy ? <LoaderCircle className="spin" size={17} /> : icon}</span><div><strong>{label}</strong><small>{detail}</small></div><ArrowRight size={15} /></button>;
}

function formatMinutes(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return hours ? `${hours}h ${remainder}m` : `${remainder}m`;
}

function statusLabel(status: AutopilotTrip['status']): string {
  return ({ RESERVED: 'Charger reserved', MONITORING: 'Monitoring live', REROUTED: 'Route updated', PAYMENT_REQUIRED: 'Payment needed', COMPLETED: 'Autopilot complete', CANCELLED: 'Cancelled' })[status];
}

function messageFor(error: unknown): string {
  return error instanceof Error ? error.message : 'The Autopilot action could not be completed.';
}
