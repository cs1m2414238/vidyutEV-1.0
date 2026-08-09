import { useCallback, useEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import {
  ArrowLeft,
  BatteryCharging,
  Bluetooth,
  Cable,
  CarFront,
  CheckCircle2,
  CircleAlert,
  Clock3,
  Gauge,
  MapPin,
  PencilLine,
  RefreshCw,
  Route,
  Smartphone,
  Unplug,
  Wallet,
  X,
  Zap,
} from 'lucide-react';
import { getVehicle, updateVehicle } from '../services/vehicles';
import type { Vehicle, VehicleConnectionStatus, VehicleUpdatePayload } from '../services/vehicles';
import './VehicleDetailView.css';

interface VehicleDetailViewProps {
  token: string;
  vehicleId: number;
  onBack: () => void;
  onFindChargers: () => void;
  onOpenWallet: () => void;
  onVehicleUpdated?: (vehicle: Vehicle) => void;
}

interface BluetoothRemoteGattServerLike {
  getPrimaryService(service: string): Promise<{
    getCharacteristic(characteristic: string): Promise<{ readValue(): Promise<DataView> }>;
  }>;
}

interface BluetoothDeviceLike {
  name?: string;
  gatt?: {
    connected: boolean;
    connect(): Promise<BluetoothRemoteGattServerLike>;
    disconnect(): void;
  };
}

interface BluetoothApiLike {
  requestDevice(options: { acceptAllDevices: true; optionalServices: string[] }): Promise<BluetoothDeviceLike>;
}

type SupportChoice = 'UNKNOWN' | 'YES' | 'NO';
type ChargingChoice = 'UNKNOWN' | 'CHARGING' | 'NOT_CHARGING';

interface TelemetryForm {
  batteryPercent: string;
  remainingRangeKm: string;
  connectionStatus: VehicleConnectionStatus;
  charging: ChargingChoice;
  bluetoothSupported: SupportChoice;
  androidAutoSupported: SupportChoice;
  appleCarPlaySupported: SupportChoice;
  lastChargingStation: string;
  lastChargingAddress: string;
  lastChargedAt: string;
}

function toSupportChoice(value?: boolean | null): SupportChoice {
  return value == null ? 'UNKNOWN' : value ? 'YES' : 'NO';
}

function toDateTimeInput(value?: string | null): string {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 16);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function formFromVehicle(vehicle: Vehicle): TelemetryForm {
  return {
    batteryPercent: vehicle.batteryPercent == null ? '' : String(vehicle.batteryPercent),
    remainingRangeKm: vehicle.remainingRangeKm == null ? '' : String(vehicle.remainingRangeKm),
    connectionStatus: vehicle.connectionStatus,
    charging: vehicle.charging == null ? 'UNKNOWN' : vehicle.charging ? 'CHARGING' : 'NOT_CHARGING',
    bluetoothSupported: toSupportChoice(vehicle.bluetoothSupported),
    androidAutoSupported: toSupportChoice(vehicle.androidAutoSupported),
    appleCarPlaySupported: toSupportChoice(vehicle.appleCarPlaySupported),
    lastChargingStation: vehicle.lastChargingStation || '',
    lastChargingAddress: vehicle.lastChargingAddress || '',
    lastChargedAt: toDateTimeInput(vehicle.lastChargedAt),
  };
}

function formatDateTime(value?: string | null): string {
  if (!value) return 'Not reported yet';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date);
}

function sourceLabel(vehicle: Vehicle): string {
  if (vehicle.telemetrySource === 'BLUETOOTH') return 'Bluetooth reading';
  if (vehicle.telemetrySource === 'CHARGING_SESSION') return 'Charging session';
  if (vehicle.telemetrySource === 'MANUAL') return 'Manual update';
  return 'No live source';
}

function capabilityLabel(value?: boolean | null): string {
  return value == null ? 'Not specified' : value ? 'Supported' : 'Not supported';
}

function supportValue(value: SupportChoice): boolean | undefined {
  return value === 'UNKNOWN' ? undefined : value === 'YES';
}

export function VehicleDetailView({
  token,
  vehicleId,
  onBack,
  onFindChargers,
  onOpenWallet,
  onVehicleUpdated,
}: VehicleDetailViewProps) {
  const [vehicle, setVehicle] = useState<Vehicle | null>(null);
  const [form, setForm] = useState<TelemetryForm | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [showEditor, setShowEditor] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const bluetoothDevice = useRef<BluetoothDeviceLike | null>(null);

  const loadVehicle = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getVehicle(token, vehicleId);
      setVehicle(data);
      setForm(formFromVehicle(data));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Unable to load this vehicle.');
    } finally {
      setLoading(false);
    }
  }, [token, vehicleId]);

  useEffect(() => {
    void loadVehicle();
  }, [loadVehicle]);

  useEffect(() => {
    if (!showEditor) return undefined;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !saving) setShowEditor(false);
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [showEditor, saving]);

  const applyUpdate = (updated: Vehicle) => {
    setVehicle(updated);
    setForm(formFromVehicle(updated));
    onVehicleUpdated?.(updated);
  };

  const openEditor = () => {
    if (!vehicle) return;
    setForm(formFromVehicle(vehicle));
    setError('');
    setNotice('');
    setShowEditor(true);
  };

  const saveTelemetry = async () => {
    if (!form) return;
    const batteryPercent = form.batteryPercent === '' ? undefined : Number(form.batteryPercent);
    const remainingRangeKm = form.remainingRangeKm === '' ? undefined : Number(form.remainingRangeKm);
    if (batteryPercent != null && (!Number.isFinite(batteryPercent) || batteryPercent < 0 || batteryPercent > 100)) {
      setError('Battery level must be between 0 and 100.');
      return;
    }
    if (remainingRangeKm != null && (!Number.isFinite(remainingRangeKm) || remainingRangeKm < 0)) {
      setError('Remaining range cannot be negative.');
      return;
    }

    const payload: VehicleUpdatePayload = {
      batteryPercent,
      remainingRangeKm,
      connectionStatus: form.connectionStatus,
      charging: form.charging === 'UNKNOWN' ? undefined : form.charging === 'CHARGING',
      bluetoothSupported: supportValue(form.bluetoothSupported),
      androidAutoSupported: supportValue(form.androidAutoSupported),
      appleCarPlaySupported: supportValue(form.appleCarPlaySupported),
      lastChargingStation: form.lastChargingStation.trim() || undefined,
      lastChargingAddress: form.lastChargingAddress.trim() || undefined,
      lastChargedAt: form.lastChargedAt ? `${form.lastChargedAt}:00` : undefined,
      telemetrySource: 'MANUAL',
    };

    setSaving(true);
    setError('');
    try {
      const updated = await updateVehicle(token, vehicleId, payload);
      applyUpdate(updated);
      setShowEditor(false);
      setNotice('Vehicle status and capabilities were updated.');
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to update this vehicle.');
    } finally {
      setSaving(false);
    }
  };

  const syncBluetooth = async () => {
    const bluetooth = (navigator as Navigator & { bluetooth?: BluetoothApiLike }).bluetooth;
    setError('');
    setNotice('');
    if (!window.isSecureContext) {
      setError('Bluetooth sync requires a secure HTTPS connection or localhost.');
      return;
    }
    if (!bluetooth) {
      setError('Web Bluetooth is not available in this browser. Use a compatible Android/desktop browser or update the data manually.');
      return;
    }

    setSyncing(true);
    try {
      const device = await bluetooth.requestDevice({
        acceptAllDevices: true,
        optionalServices: ['battery_service'],
      });
      bluetoothDevice.current = device;
      if (!device.gatt) throw new Error('The selected device does not expose a Bluetooth data connection.');
      const server = await device.gatt.connect();
      const payload: VehicleUpdatePayload = {
        connectionStatus: 'CONNECTED',
        bluetoothSupported: true,
        bluetoothDeviceName: device.name || 'Bluetooth vehicle',
        telemetrySource: 'BLUETOOTH',
      };

      let batteryRead = false;
      try {
        const service = await server.getPrimaryService('battery_service');
        const characteristic = await service.getCharacteristic('battery_level');
        const value = await characteristic.readValue();
        payload.batteryPercent = value.getUint8(0);
        batteryRead = true;
      } catch {
        // Most vehicle makers protect telemetry behind proprietary services.
      }

      const updated = await updateVehicle(token, vehicleId, payload);
      applyUpdate(updated);
      setNotice(batteryRead
        ? `Connected to ${device.name || 'the selected device'} and read its battery level.`
        : `Connected to ${device.name || 'the selected device'}. Its battery level is not exposed through the standard Bluetooth Battery Service.`);
    } catch (syncError) {
      const bluetoothError = syncError as Error;
      if (bluetoothError.name !== 'NotFoundError') {
        setError(bluetoothError.message || 'Unable to connect to the selected Bluetooth device.');
      }
    } finally {
      setSyncing(false);
    }
  };

  const disconnectBluetooth = async () => {
    setSyncing(true);
    setError('');
    try {
      bluetoothDevice.current?.gatt?.disconnect();
      bluetoothDevice.current = null;
      const updated = await updateVehicle(token, vehicleId, {
        connectionStatus: 'DISCONNECTED',
        telemetrySource: 'BLUETOOTH',
      });
      applyUpdate(updated);
      setNotice('Bluetooth was disconnected. Your last reading remains saved.');
    } catch (disconnectError) {
      setError(disconnectError instanceof Error ? disconnectError.message : 'Unable to update the connection status.');
    } finally {
      setSyncing(false);
    }
  };

  if (loading) {
    return (
      <section className="vehicle-detail-page vehicle-detail-loading" aria-live="polite">
        <RefreshCw className="spinning" size={25} />
        <strong>Loading vehicle data…</strong>
      </section>
    );
  }

  if (!vehicle) {
    return (
      <section className="vehicle-detail-page vehicle-detail-missing">
        <CircleAlert size={35} />
        <h1>Vehicle unavailable</h1>
        <p>{error || 'This vehicle could not be found in your account.'}</p>
        <button type="button" className="vehicle-secondary-button" onClick={onBack}><ArrowLeft size={16} /> Back to my vehicles</button>
      </section>
    );
  }

  const battery = vehicle.batteryPercent == null ? null : Math.max(0, Math.min(100, vehicle.batteryPercent));
  const connectionClass = vehicle.connectionStatus.toLowerCase();
  const chargeLabel = vehicle.charging == null ? 'Not reported' : vehicle.charging ? 'Charging' : 'Not charging';
  const ringStyle = { '--vehicle-battery-level': `${battery ?? 0}%` } as CSSProperties;

  return (
    <section className="vehicle-detail-page" aria-labelledby="vehicle-detail-title">
      <button type="button" className="vehicle-detail-back" onClick={onBack}><ArrowLeft size={16} /> My vehicles</button>

      <header className="vehicle-detail-header">
        <div className="vehicle-detail-title-wrap">
          <span className="vehicle-detail-title-icon"><CarFront size={26} /></span>
          <div>
            <span>{vehicle.registrationNumber}</span>
            <h1 id="vehicle-detail-title">{vehicle.makeAndModel}</h1>
            <p>{sourceLabel(vehicle)} · Updated {formatDateTime(vehicle.telemetryUpdatedAt)}</p>
          </div>
        </div>
        <div className="vehicle-detail-header-actions">
          <span className={`vehicle-detail-status ${connectionClass}`}><i />{vehicle.connectionStatus === 'CONNECTED' ? 'Connected' : vehicle.connectionStatus === 'DISCONNECTED' ? 'Offline' : 'Not synced'}</span>
          <button type="button" className="vehicle-secondary-button" onClick={openEditor}><PencilLine size={15} /> Update data</button>
        </div>
      </header>

      {error && <div className="vehicle-detail-message error" role="alert"><CircleAlert size={17} />{error}</div>}
      {notice && <div className="vehicle-detail-message success" role="status"><CheckCircle2 size={17} />{notice}</div>}

      <div className="vehicle-detail-metrics">
        <article>
          <span><Bluetooth size={19} /></span>
          <small>Connection</small>
          <strong>{vehicle.connectionStatus === 'CONNECTED' ? 'Connected' : vehicle.connectionStatus === 'DISCONNECTED' ? 'Offline' : 'Not synced'}</strong>
          <p>{vehicle.bluetoothDeviceName || 'No device name saved'}</p>
        </article>
        <article>
          <span><BatteryCharging size={19} /></span>
          <small>Battery remaining</small>
          <strong>{battery == null ? 'Not synced' : `${battery}%`}</strong>
          <p>{sourceLabel(vehicle)}</p>
        </article>
        <article>
          <span><Route size={19} /></span>
          <small>Estimated range</small>
          <strong>{vehicle.remainingRangeKm == null ? 'Not synced' : `${Math.round(vehicle.remainingRangeKm)} km`}</strong>
          <p>Remaining driving estimate</p>
        </article>
        <article className={vehicle.charging ? 'is-charging' : ''}>
          <span><Zap size={19} /></span>
          <small>Charge status</small>
          <strong>{chargeLabel}</strong>
          <p>{vehicle.charging ? 'Energy is being received' : vehicle.charging === false ? 'Vehicle is not drawing power' : 'Awaiting a reading'}</p>
        </article>
      </div>

      <div className="vehicle-detail-grid">
        <article className={`vehicle-live-card ${vehicle.charging ? 'charging' : ''}`}>
          <div className="vehicle-live-copy">
            <span className="vehicle-live-eyebrow">CURRENT VEHICLE STATE</span>
            <h2>{vehicle.charging ? 'Charging now' : vehicle.connectionStatus === 'CONNECTED' ? 'Vehicle connected' : 'Ready when you are'}</h2>
            <p>{vehicle.charging
              ? 'This status is based on the latest saved vehicle reading.'
              : 'Connect by Bluetooth or update the status to keep the dashboard accurate.'}</p>
            <div className="vehicle-live-actions">
              <button type="button" onClick={() => void syncBluetooth()} disabled={syncing}><Bluetooth size={16} />{syncing ? 'Connecting…' : 'Sync Bluetooth'}</button>
              {vehicle.connectionStatus === 'CONNECTED' && (
                <button type="button" className="ghost" onClick={() => void disconnectBluetooth()} disabled={syncing}><Unplug size={16} /> Disconnect</button>
              )}
            </div>
          </div>
          <div className="vehicle-battery-ring" style={ringStyle}>
            <div>
              <BatteryCharging size={25} />
              <strong>{battery == null ? '—' : `${battery}%`}</strong>
              <span>{battery == null ? 'Not synced' : 'Remaining'}</span>
            </div>
          </div>
        </article>

        <article className="vehicle-info-card vehicle-last-charge-card">
          <div className="vehicle-info-card-head">
            <span><MapPin size={18} /></span>
            <div><h2>Last charge</h2><p>The most recent refill saved for this vehicle</p></div>
          </div>
          {vehicle.lastChargingStation || vehicle.lastChargedAt ? (
            <div className="vehicle-last-charge-content">
              <strong>{vehicle.lastChargingStation || 'Charging location not named'}</strong>
              <p><MapPin size={14} />{vehicle.lastChargingAddress || 'Address not recorded'}</p>
              <p><Clock3 size={14} />{formatDateTime(vehicle.lastChargedAt)}</p>
            </div>
          ) : (
            <div className="vehicle-info-empty">
              <Zap size={24} />
              <strong>No charge recorded yet</strong>
              <p>Add the last station manually; a linked charging session can supply it later.</p>
            </div>
          )}
        </article>

        <article className="vehicle-info-card vehicle-capabilities-card">
          <div className="vehicle-info-card-head">
            <span><Smartphone size={18} /></span>
            <div><h2>Connectivity & infotainment</h2><p>Capabilities saved for this model</p></div>
          </div>
          <ul className="vehicle-capability-list">
            <li><span><Bluetooth size={17} /> Bluetooth telemetry</span><strong className={vehicle.bluetoothSupported ? 'supported' : ''}>{capabilityLabel(vehicle.bluetoothSupported)}</strong></li>
            <li><span><Smartphone size={17} /> Android Auto</span><strong className={vehicle.androidAutoSupported ? 'supported' : ''}>{capabilityLabel(vehicle.androidAutoSupported)}</strong></li>
            <li><span><Smartphone size={17} /> Apple CarPlay</span><strong className={vehicle.appleCarPlaySupported ? 'supported' : ''}>{capabilityLabel(vehicle.appleCarPlaySupported)}</strong></li>
          </ul>
          <p className="vehicle-capability-note"><CircleAlert size={14} /> Android Auto and Apple CarPlay cannot be detected by a web page; these are owner-confirmed capabilities.</p>
        </article>

        <article className="vehicle-info-card vehicle-spec-card">
          <div className="vehicle-info-card-head">
            <span><Gauge size={18} /></span>
            <div><h2>Vehicle specification</h2><p>Used for compatible charger recommendations</p></div>
          </div>
          <dl className="vehicle-spec-list">
            <div><dt><Cable size={15} /> Connector</dt><dd>{vehicle.connectorType || 'Not set'}</dd></div>
            <div><dt><BatteryCharging size={15} /> Battery capacity</dt><dd>{vehicle.batteryCapacity || 'Not set'}</dd></div>
            <div><dt><CarFront size={15} /> Registration</dt><dd>{vehicle.registrationNumber}</dd></div>
          </dl>
        </article>
      </div>

      <aside className="vehicle-data-explainer">
        <div>
          <CircleAlert size={19} />
          <p><strong>How Vidyut decides “charging” or “connected”</strong><br />The dashboard uses this vehicle’s latest saved reading. Bluetooth can read a battery only when the vehicle exposes the standard Battery Service; many manufacturers use protected apps and proprietary services.</p>
        </div>
        <button type="button" onClick={openEditor}>Review data</button>
      </aside>

      <div className="vehicle-detail-bottom-actions">
        <button type="button" onClick={onFindChargers}><MapPin size={17} /><span><strong>Find a compatible charger</strong><small>Filtered using {vehicle.connectorType || 'your connector'}</small></span></button>
        <button type="button" onClick={onOpenWallet}><Wallet size={17} /><span><strong>Open wallet</strong><small>Manage charging payments</small></span></button>
      </div>

      {showEditor && form && (
        <div className="vehicle-detail-modal-backdrop" role="presentation" onMouseDown={() => !saving && setShowEditor(false)}>
          <section className="vehicle-detail-modal" role="dialog" aria-modal="true" aria-labelledby="vehicle-update-title" onMouseDown={(event) => event.stopPropagation()}>
            <header>
              <div><span>VEHICLE TELEMETRY</span><h2 id="vehicle-update-title">Update {vehicle.makeAndModel}</h2><p>Record only what you know. Unknown values remain clearly marked.</p></div>
              <button type="button" onClick={() => setShowEditor(false)} disabled={saving} aria-label="Close vehicle update"><X size={19} /></button>
            </header>
            <form onSubmit={(event) => { event.preventDefault(); void saveTelemetry(); }}>
              {error && <div className="vehicle-detail-message error" role="alert"><CircleAlert size={16} />{error}</div>}
              <div className="vehicle-telemetry-fields">
                <label>Battery remaining (%)<input type="number" min="0" max="100" step="1" value={form.batteryPercent} onChange={(event) => setForm({ ...form, batteryPercent: event.target.value })} placeholder="68" /></label>
                <label>Estimated range (km)<input type="number" min="0" step="0.1" value={form.remainingRangeKm} onChange={(event) => setForm({ ...form, remainingRangeKm: event.target.value })} placeholder="246" /></label>
                <label>Connection status<select value={form.connectionStatus} onChange={(event) => setForm({ ...form, connectionStatus: event.target.value as VehicleConnectionStatus })}><option value="UNKNOWN">Not synced</option><option value="CONNECTED">Connected</option><option value="DISCONNECTED">Disconnected</option></select></label>
                <label>Charging status<select value={form.charging} onChange={(event) => setForm({ ...form, charging: event.target.value as ChargingChoice })}><option value="UNKNOWN">Not reported</option><option value="CHARGING">Charging</option><option value="NOT_CHARGING">Not charging</option></select></label>
                <label>Bluetooth telemetry<select value={form.bluetoothSupported} onChange={(event) => setForm({ ...form, bluetoothSupported: event.target.value as SupportChoice })}><option value="UNKNOWN">Not specified</option><option value="YES">Supported</option><option value="NO">Not supported</option></select></label>
                <label>Android Auto<select value={form.androidAutoSupported} onChange={(event) => setForm({ ...form, androidAutoSupported: event.target.value as SupportChoice })}><option value="UNKNOWN">Not specified</option><option value="YES">Supported</option><option value="NO">Not supported</option></select></label>
                <label>Apple CarPlay<select value={form.appleCarPlaySupported} onChange={(event) => setForm({ ...form, appleCarPlaySupported: event.target.value as SupportChoice })}><option value="UNKNOWN">Not specified</option><option value="YES">Supported</option><option value="NO">Not supported</option></select></label>
                <label>Last charged at<input type="datetime-local" value={form.lastChargedAt} onChange={(event) => setForm({ ...form, lastChargedAt: event.target.value })} /></label>
                <label className="wide">Last charging station<input value={form.lastChargingStation} onChange={(event) => setForm({ ...form, lastChargingStation: event.target.value })} placeholder="Green Park Station" maxLength={160} /></label>
                <label className="wide">Charging address<input value={form.lastChargingAddress} onChange={(event) => setForm({ ...form, lastChargingAddress: event.target.value })} placeholder="Green Park Extension, New Delhi" maxLength={255} /></label>
              </div>
              <footer>
                <button type="button" className="vehicle-secondary-button" onClick={() => setShowEditor(false)} disabled={saving}>Cancel</button>
                <button type="submit" className="vehicle-primary-button" disabled={saving}>{saving ? 'Saving…' : 'Save vehicle data'}</button>
              </footer>
            </form>
          </section>
        </div>
      )}
    </section>
  );
}
