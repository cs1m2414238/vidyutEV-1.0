import { useCallback, useEffect, useState } from 'react';
import {
  BatteryCharging,
  Bluetooth,
  CarFront,
  CircleAlert,
  Gauge,
  MapPin,
  Plus,
  RefreshCw,
  ShieldCheck,
  Trash2,
  X,
  Zap,
} from 'lucide-react';
import { addVehicle as createVehicle, deleteVehicle, getVehicles } from '../services/vehicles';
import type { Vehicle } from '../services/vehicles';
import './VehiclesView.css';

interface VehicleForm {
  makeAndModel: string;
  registrationNumber: string;
  batteryCapacity: string;
  connectorType: string;
}

const emptyForm: VehicleForm = {
  makeAndModel: '',
  registrationNumber: '',
  batteryCapacity: '',
  connectorType: 'CCS2',
};

function normalizeRegistration(value: string): string {
  return value.trim().toUpperCase().replace(/\s+/g, ' ');
}

function capacityNumber(value?: string | null): number | null {
  if (!value) return null;
  const parsed = Number.parseFloat(value.replace(/[^0-9.]/g, ''));
  return Number.isFinite(parsed) ? parsed : null;
}

export function VehiclesView({
  token,
  onFindChargers,
  onOpenWallet,
  onOpenVehicle,
}: {
  token: string;
  onFindChargers: () => void;
  onOpenWallet: () => void;
  onOpenVehicle: (vehicleId: number) => void;
}) {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [form, setForm] = useState<VehicleForm>(emptyForm);
  const [showForm, setShowForm] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Vehicle | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');
  const [formError, setFormError] = useState('');
  const [notice, setNotice] = useState('');

  const loadVehicles = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getVehicles(token);
      setVehicles(data);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Unable to load your vehicles.');
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void loadVehicles();
  }, [loadVehicles]);

  useEffect(() => {
    if (!showForm && !pendingDelete) return undefined;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || saving || deleting) return;
      setShowForm(false);
      setPendingDelete(null);
      setFormError('');
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [showForm, pendingDelete, saving, deleting]);

  const connectorCount = new Set(
    vehicles.map((vehicle) => vehicle.connectorType?.trim()).filter(Boolean),
  ).size;
  const capacities = vehicles
    .map((vehicle) => capacityNumber(vehicle.batteryCapacity))
    .filter((capacity): capacity is number => capacity !== null);
  const totalCapacity = capacities.reduce((total, capacity) => total + capacity, 0);
  const connectedCount = vehicles.filter((vehicle) => vehicle.connectionStatus === 'CONNECTED').length;

  const openAddVehicle = () => {
    setForm(emptyForm);
    setFormError('');
    setNotice('');
    setShowForm(true);
  };

  const closeAddVehicle = () => {
    if (saving) return;
    setShowForm(false);
    setFormError('');
  };

  const handleAddVehicle = async () => {
    const makeAndModel = form.makeAndModel.trim();
    const registrationNumber = normalizeRegistration(form.registrationNumber);
    if (!makeAndModel || !registrationNumber) {
      setFormError('Make and model and registration number are required.');
      return;
    }

    setSaving(true);
    setFormError('');
    try {
      const vehicle = await createVehicle(token, {
        makeAndModel,
        registrationNumber,
        batteryCapacity: form.batteryCapacity.trim() || undefined,
        connectorType: form.connectorType,
      });
      setVehicles((current) => [...current, vehicle]);
      setShowForm(false);
      setForm(emptyForm);
      setNotice(`${vehicle.makeAndModel} was added to your account.`);
    } catch (saveError) {
      setFormError(saveError instanceof Error ? saveError.message : 'Unable to add your vehicle.');
    } finally {
      setSaving(false);
    }
  };

  const removeVehicle = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    setError('');
    try {
      await deleteVehicle(token, pendingDelete.id);
      setVehicles((current) => current.filter((vehicle) => vehicle.id !== pendingDelete.id));
      setNotice(`${pendingDelete.makeAndModel} was removed from your account.`);
      setPendingDelete(null);
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : 'Unable to remove this vehicle.');
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <section className="vehicles-page" aria-labelledby="vehicles-title">
      <header className="vehicles-heading">
        <div>
          <div className="feature-eyebrow">Vidyut workspace</div>
          <h1 id="vehicles-title">My vehicles</h1>
          <p>Keep battery and connector information ready for smarter recommendations.</p>
        </div>
        <button className="feature-primary vehicles-add-button" type="button" onClick={openAddVehicle}>
          <CarFront size={17} /> Add vehicle
        </button>
      </header>

      {error && <div className="vehicles-message error" role="alert"><CircleAlert size={16} />{error}</div>}
      {notice && <div className="vehicles-message success" role="status"><ShieldCheck size={16} />{notice}</div>}

      <div className="feature-metrics vehicles-metrics">
        <article className="feature-metric">
          <span className="feature-metric-icon"><CarFront size={18} /></span>
          <div className="feature-metric-value">{loading ? '—' : vehicles.length}</div>
          <div className="feature-metric-label">Vehicles saved to your garage</div>
        </article>
        <article className="feature-metric">
          <span className="feature-metric-icon"><Bluetooth size={18} /></span>
          <div className="feature-metric-value">{loading ? '—' : connectedCount}</div>
          <div className="feature-metric-label">Currently connected</div>
        </article>
        <article className="feature-metric">
          <span className="feature-metric-icon"><BatteryCharging size={18} /></span>
          <div className="feature-metric-value">{loading ? '—' : totalCapacity > 0 ? `${totalCapacity.toFixed(totalCapacity % 1 ? 1 : 0)} kWh` : 'Not set'}</div>
          <div className="feature-metric-label">Combined battery capacity</div>
        </article>
      </div>

      <div className="vehicles-layout">
        <article className="vehicles-panel">
          <div className="vehicles-panel-head">
            <div><h2>Your garage</h2><p>Saved securely to your Vidyut account</p></div>
            <button className="vehicles-refresh" type="button" onClick={() => void loadVehicles()} disabled={loading}>
              <RefreshCw size={14} className={loading ? 'spinning' : ''} /> Refresh
            </button>
          </div>

          {loading ? (
            <div className="vehicles-loading" aria-live="polite">
              <span /><span /><span />
              <p>Loading your vehicles…</p>
            </div>
          ) : vehicles.length === 0 ? (
            <div className="vehicles-empty">
              <span><CarFront size={29} /></span>
              <h3>Add your first EV</h3>
              <p>Your vehicle details help Vidyut show compatible chargers and prepare charging preferences.</p>
              <button className="feature-primary" type="button" onClick={openAddVehicle}><Plus size={15} /> Add vehicle</button>
            </div>
          ) : (
            <ul className="vehicles-list">
              {vehicles.map((vehicle) => (
                <li className="vehicle-card-row" key={vehicle.id}>
                  <button className="vehicle-card-open" type="button" onClick={() => onOpenVehicle(vehicle.id)} aria-label={`Open ${vehicle.makeAndModel} details`}>
                    <span className="vehicle-card-icon"><CarFront size={20} /></span>
                    <div className="vehicle-card-copy">
                      <span className="vehicle-registration">{vehicle.registrationNumber}</span>
                      <h3>{vehicle.makeAndModel}</h3>
                      <div className="vehicle-specs">
                        <span><Zap size={13} />{vehicle.connectorType || 'Connector not set'}</span>
                        <span><BatteryCharging size={13} />{vehicle.batteryPercent != null ? `${vehicle.batteryPercent}% battery` : vehicle.batteryCapacity || 'Battery not synced'}</span>
                      </div>
                    </div>
                    <span className={`vehicle-connection-pill ${vehicle.connectionStatus.toLowerCase()}`}><i />{vehicle.connectionStatus === 'CONNECTED' ? 'Connected' : vehicle.connectionStatus === 'DISCONNECTED' ? 'Offline' : 'Not synced'}</span>
                  </button>
                  <div className="vehicle-row-actions">
                    <button type="button" onClick={() => onOpenVehicle(vehicle.id)}><Gauge size={14} /> View details</button>
                    <button className="danger" type="button" onClick={() => setPendingDelete(vehicle)} aria-label={`Remove ${vehicle.makeAndModel}`}><Trash2 size={14} /> Remove</button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </article>

        <aside className="feature-tip vehicles-insight">
          <Gauge size={24} />
          <h3>Charge smarter</h3>
          <p>{connectorCount} connector {connectorCount === 1 ? 'type is' : 'types are'} saved. Open a vehicle to review range, connection, charging status and infotainment support.</p>
          <div className="vehicles-insight-actions">
            <button type="button" onClick={onFindChargers}>Find compatible chargers <MapPin size={13} /></button>
            <button type="button" onClick={onOpenWallet}>Set up auto-recharge <Zap size={13} /></button>
          </div>
        </aside>
      </div>

      {showForm && (
        <div className="vehicle-form-backdrop" role="presentation" onMouseDown={closeAddVehicle}>
          <section className="vehicle-form-modal vehicles-form-modal" role="dialog" aria-modal="true" aria-labelledby="add-vehicle-title" onMouseDown={(event) => event.stopPropagation()}>
            <div className="vehicles-modal-head">
              <span className="vehicles-modal-icon"><CarFront size={23} /></span>
              <div><h2 id="add-vehicle-title">Add your vehicle</h2><p>Save it once and use it everywhere in your EV Owner workspace.</p></div>
              <button className="modal-close" type="button" onClick={closeAddVehicle} aria-label="Close add vehicle form"><X size={18} /></button>
            </div>
            <form onSubmit={(event) => { event.preventDefault(); void handleAddVehicle(); }}>
              {formError && <div className="vehicles-form-error" role="alert"><CircleAlert size={15} />{formError}</div>}
              <div className="vehicle-form-fields vehicles-form-fields">
                <label>
                  Make and model <strong aria-hidden="true">*</strong>
                  <input value={form.makeAndModel} onChange={(event) => setForm((current) => ({ ...current, makeAndModel: event.target.value }))} placeholder="Tata Nexon EV" maxLength={120} autoFocus required />
                </label>
                <label>
                  Registration number <strong aria-hidden="true">*</strong>
                  <input value={form.registrationNumber} onChange={(event) => setForm((current) => ({ ...current, registrationNumber: event.target.value.toUpperCase() }))} placeholder="UP32 AB 1234" maxLength={30} autoCapitalize="characters" required />
                </label>
                <label>
                  Battery capacity
                  <input value={form.batteryCapacity} onChange={(event) => setForm((current) => ({ ...current, batteryCapacity: event.target.value }))} placeholder="40.5 kWh" maxLength={30} />
                </label>
                <label>
                  Connector type
                  <select value={form.connectorType} onChange={(event) => setForm((current) => ({ ...current, connectorType: event.target.value }))}>
                    <option value="CCS2">CCS2</option>
                    <option value="Type 2">Type 2</option>
                    <option value="CHAdeMO">CHAdeMO</option>
                    <option value="Bharat DC-001">Bharat DC-001</option>
                    <option value="Bharat AC-001">Bharat AC-001</option>
                  </select>
                </label>
              </div>
              <p className="vehicles-form-note"><ShieldCheck size={14} /> Required fields are marked with an asterisk. Battery capacity is optional.</p>
              <div className="vehicle-form-actions">
                <button className="secondary-action" type="button" onClick={closeAddVehicle} disabled={saving}>Cancel</button>
                <button className="feature-primary" type="submit" disabled={saving}>{saving ? 'Adding…' : 'Add vehicle'}</button>
              </div>
            </form>
          </section>
        </div>
      )}

      {pendingDelete && (
        <div className="vehicle-form-backdrop" role="presentation" onMouseDown={() => !deleting && setPendingDelete(null)}>
          <section className="vehicle-delete-dialog" role="alertdialog" aria-modal="true" aria-labelledby="remove-vehicle-title" onMouseDown={(event) => event.stopPropagation()}>
            <span><Trash2 size={23} /></span>
            <h2 id="remove-vehicle-title">Remove this vehicle?</h2>
            <p><strong>{pendingDelete.makeAndModel}</strong> ({pendingDelete.registrationNumber}) will be removed from your account and linked auto-recharge rules.</p>
            <div className="vehicle-form-actions">
              <button className="secondary-action" type="button" onClick={() => setPendingDelete(null)} disabled={deleting}>Keep vehicle</button>
              <button className="vehicles-danger-button" type="button" onClick={() => void removeVehicle()} disabled={deleting}>{deleting ? 'Removing…' : 'Remove vehicle'}</button>
            </div>
          </section>
        </div>
      )}
    </section>
  );
}
