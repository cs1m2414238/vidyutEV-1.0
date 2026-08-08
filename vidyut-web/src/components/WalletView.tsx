import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowDownLeft,
  ArrowUpRight,
  CarFront,
  CheckCircle2,
  CreditCard,
  Plus,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  WalletCards,
} from 'lucide-react';
import { apiRequest } from '../services/api';

interface Vehicle {
  id: number;
  makeAndModel: string;
  registrationNumber: string;
  batteryCapacity?: string;
  connectorType?: string;
}

interface WalletTransaction {
  id: number;
  vehicleId?: number | null;
  amount: number;
  type: 'TOP_UP' | 'AUTO_RECHARGE' | 'CHARGING_PAYMENT' | string;
  description: string;
  timestamp: string;
}

interface WalletData {
  walletId: number;
  userId: number;
  balance: number;
  recentTransactions: WalletTransaction[];
}

interface AutoRechargeRule {
  id: number;
  vehicleId: number;
  vehicleName: string;
  registrationNumber: string;
  enabled: boolean;
  balanceThreshold: number;
  rechargeAmount: number;
  paymentMethod: string;
  lastTriggeredAt?: string | null;
  updatedAt: string;
}

interface RuleDraft {
  enabled: boolean;
  balanceThreshold: number;
  rechargeAmount: number;
  paymentMethod: string;
}

const defaultRule: RuleDraft = {
  enabled: true,
  balanceThreshold: 500,
  rechargeAmount: 1000,
  paymentMethod: 'UPI mandate',
};

function rupees(value: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(value);
}

function friendlyDate(value?: string | null): string {
  if (!value) return 'Not triggered yet';
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value));
}

export function WalletView({ token, onOpenVehicles }: { token: string; onOpenVehicles: () => void }) {
  const [wallet, setWallet] = useState<WalletData | null>(null);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [rules, setRules] = useState<AutoRechargeRule[]>([]);
  const [selectedVehicleId, setSelectedVehicleId] = useState<number | null>(null);
  const [draft, setDraft] = useState<RuleDraft>(defaultRule);
  const [topUpAmount, setTopUpAmount] = useState(1000);
  const [showVehicleForm, setShowVehicleForm] = useState(false);
  const [vehicleForm, setVehicleForm] = useState({ makeAndModel: '', registrationNumber: '', batteryCapacity: '', connectorType: 'CCS2' });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');

  const authInit = useMemo(() => ({ headers: { Authorization: `Bearer ${token}` } }), [token]);

  const loadWallet = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [walletData, vehicleData, ruleData] = await Promise.all([
        apiRequest<WalletData>('/ev/wallet', { method: 'GET', ...authInit }),
        apiRequest<Vehicle[]>('/ev/vehicles', { method: 'GET', ...authInit }),
        apiRequest<AutoRechargeRule[]>('/ev/wallet/auto-recharge', { method: 'GET', ...authInit }),
      ]);
      setWallet(walletData);
      setVehicles(vehicleData);
      setRules(ruleData);
      setSelectedVehicleId((current) => current && vehicleData.some((vehicle) => vehicle.id === current) ? current : vehicleData[0]?.id ?? null);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Unable to load your wallet.');
    } finally {
      setLoading(false);
    }
  }, [authInit]);

  useEffect(() => {
    void loadWallet();
  }, [loadWallet]);

  useEffect(() => {
    if (!selectedVehicleId) {
      setDraft(defaultRule);
      return;
    }
    const rule = rules.find((item) => item.vehicleId === selectedVehicleId);
    setDraft(rule ? {
      enabled: rule.enabled,
      balanceThreshold: rule.balanceThreshold,
      rechargeAmount: rule.rechargeAmount,
      paymentMethod: rule.paymentMethod,
    } : defaultRule);
  }, [selectedVehicleId, rules]);

  const selectedVehicle = vehicles.find((vehicle) => vehicle.id === selectedVehicleId);
  const selectedRule = rules.find((rule) => rule.vehicleId === selectedVehicleId);
  const activeRuleCount = rules.filter((rule) => rule.enabled).length;

  const topUp = async () => {
    if (topUpAmount < 100) {
      setError('Top-up amount must be at least ₹100.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const nextWallet = await apiRequest<WalletData>('/ev/wallet/topup', {
        method: 'POST',
        ...authInit,
        body: JSON.stringify({ amount: topUpAmount }),
      });
      setWallet(nextWallet);
      setNotice(`${rupees(topUpAmount)} added to your wallet.`);
    } catch (topUpError) {
      setError(topUpError instanceof Error ? topUpError.message : 'Unable to top up your wallet.');
    } finally {
      setSaving(false);
    }
  };

  const saveRule = async () => {
    if (!selectedVehicleId) return;
    setSaving(true);
    setError('');
    try {
      const saved = await apiRequest<AutoRechargeRule>(`/ev/wallet/auto-recharge/${selectedVehicleId}`, {
        method: 'PUT',
        ...authInit,
        body: JSON.stringify({ vehicleId: selectedVehicleId, ...draft }),
      });
      setRules((current) => [saved, ...current.filter((rule) => rule.vehicleId !== saved.vehicleId)]);
      setNotice(saved.enabled
        ? `Auto-recharge is active for ${saved.vehicleName}.`
        : `Auto-recharge is paused for ${saved.vehicleName}.`);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to update auto-recharge.');
    } finally {
      setSaving(false);
    }
  };

  const addVehicle = async () => {
    if (!vehicleForm.makeAndModel.trim() || !vehicleForm.registrationNumber.trim()) {
      setError('Vehicle name and registration number are required.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const vehicle = await apiRequest<Vehicle>('/ev/vehicles', {
        method: 'POST',
        ...authInit,
        body: JSON.stringify(vehicleForm),
      });
      setVehicles((current) => [...current, vehicle]);
      setSelectedVehicleId(vehicle.id);
      setVehicleForm({ makeAndModel: '', registrationNumber: '', batteryCapacity: '', connectorType: 'CCS2' });
      setShowVehicleForm(false);
      setNotice(`${vehicle.makeAndModel} is ready for auto-recharge.`);
    } catch (vehicleError) {
      setError(vehicleError instanceof Error ? vehicleError.message : 'Unable to add your vehicle.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="wallet-page" aria-labelledby="wallet-title">
      <header className="wallet-heading">
        <div>
          <div className="feature-eyebrow">EV OWNER WALLET</div>
          <h1 id="wallet-title">Wallet & auto-recharge</h1>
          <p>Keep charging payments moving with a separate rule for each vehicle.</p>
        </div>
        <button className="wallet-refresh" onClick={() => void loadWallet()} disabled={loading}>
          <RefreshCw size={15} className={loading ? 'spinning' : ''} /> Refresh
        </button>
      </header>

      {error && <div className="wallet-message error" role="alert">{error}</div>}
      {notice && <div className="wallet-message success" role="status"><CheckCircle2 size={15} />{notice}</div>}

      <div className="wallet-summary-grid">
        <article className="wallet-balance-card">
          <div className="wallet-card-label"><WalletCards size={16} /> Available balance</div>
          <strong>{loading ? '—' : rupees(wallet?.balance ?? 0)}</strong>
          <span>Secured for Vidyut charging payments</span>
          <div className="wallet-topup-row">
            <label>
              <span className="sr-only">Top-up amount</span>
              <span className="currency-prefix">₹</span>
              <input type="number" min="100" step="100" value={topUpAmount} onChange={(event) => setTopUpAmount(Number(event.target.value))} />
            </label>
            <button onClick={() => void topUp()} disabled={saving || loading}><Plus size={15} /> Add money</button>
          </div>
        </article>

        <article className="wallet-stat-card">
          <div className="wallet-stat-icon"><CarFront size={20} /></div>
          <div><strong>{vehicles.length}</strong><span>Linked vehicles</span></div>
        </article>
        <article className="wallet-stat-card">
          <div className="wallet-stat-icon purple"><Sparkles size={20} /></div>
          <div><strong>{activeRuleCount}</strong><span>Active auto-recharge rules</span></div>
        </article>
      </div>

      <div className="wallet-content-grid">
        <section className="wallet-panel auto-panel">
          <div className="wallet-panel-head">
            <div>
              <h2>Vehicle-linked auto-recharge</h2>
              <p>A vehicle rule runs only when that vehicle completes a charging payment.</p>
            </div>
            <ShieldCheck size={22} />
          </div>

          {vehicles.length ? (
            <>
              <div className="vehicle-tabs" role="tablist" aria-label="Choose a vehicle">
                {vehicles.map((vehicle) => {
                  const rule = rules.find((item) => item.vehicleId === vehicle.id);
                  return (
                    <button key={vehicle.id} className={selectedVehicleId === vehicle.id ? 'active' : ''} onClick={() => setSelectedVehicleId(vehicle.id)} role="tab" aria-selected={selectedVehicleId === vehicle.id}>
                      <CarFront size={17} />
                      <span><strong>{vehicle.makeAndModel}</strong><small>{vehicle.registrationNumber}</small></span>
                      <i className={rule?.enabled ? 'enabled' : ''}>{rule?.enabled ? 'On' : 'Off'}</i>
                    </button>
                  );
                })}
                <button className="add-vehicle-tab" onClick={() => setShowVehicleForm(true)}><Plus size={17} /><span><strong>Add vehicle</strong><small>Create another rule</small></span></button>
              </div>

              {selectedVehicle && (
                <div className="auto-rule-editor">
                  <div className="auto-rule-title">
                    <div>
                      <span className="vehicle-reg">{selectedVehicle.registrationNumber}</span>
                      <h3>{selectedVehicle.makeAndModel}</h3>
                    </div>
                    <label className="switch-control">
                      <input type="checkbox" checked={draft.enabled} onChange={(event) => setDraft((current) => ({ ...current, enabled: event.target.checked }))} />
                      <span aria-hidden="true" />
                      {draft.enabled ? 'Enabled' : 'Paused'}
                    </label>
                  </div>

                  <div className="auto-flow">
                    <div><span>1</span><strong>Vehicle charge ends</strong><small>Payment is linked to this vehicle</small></div>
                    <div><span>2</span><strong>Balance check</strong><small>If balance falls below {rupees(draft.balanceThreshold)}</small></div>
                    <div><span>3</span><strong>Recharge wallet</strong><small>Add {rupees(draft.rechargeAmount)} automatically</small></div>
                  </div>

                  <div className="rule-fields">
                    <label>Recharge when balance goes below
                      <div className="money-input"><span>₹</span><input type="number" min="100" max="10000" step="100" value={draft.balanceThreshold} onChange={(event) => setDraft((current) => ({ ...current, balanceThreshold: Number(event.target.value) }))} /></div>
                    </label>
                    <label>Recharge amount
                      <div className="money-input"><span>₹</span><input type="number" min="100" max="25000" step="100" value={draft.rechargeAmount} onChange={(event) => setDraft((current) => ({ ...current, rechargeAmount: Number(event.target.value) }))} /></div>
                    </label>
                    <label>Payment method
                      <select value={draft.paymentMethod} onChange={(event) => setDraft((current) => ({ ...current, paymentMethod: event.target.value }))}>
                        <option>UPI mandate</option>
                        <option>Visa •••• 4242</option>
                        <option>Mastercard •••• 8841</option>
                      </select>
                    </label>
                  </div>

                  <div className="auto-rule-footer">
                    <span><CreditCard size={15} /> Last auto-recharge: {friendlyDate(selectedRule?.lastTriggeredAt)}</span>
                    <button className="feature-primary" onClick={() => void saveRule()} disabled={saving}>{saving ? 'Saving…' : 'Save auto-recharge'}</button>
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="wallet-empty">
              <div><CarFront size={27} /></div>
              <h3>Add your EV first</h3>
              <p>Auto-recharge is vehicle-specific, so link the EV you use for bookings.</p>
              <button className="feature-primary" onClick={() => setShowVehicleForm(true)}><Plus size={15} /> Add vehicle</button>
            </div>
          )}
        </section>

        <section className="wallet-panel transaction-panel">
          <div className="wallet-panel-head">
            <div><h2>Recent wallet activity</h2><p>Top-ups and charging payments</p></div>
          </div>
          <div className="transaction-list">
            {(wallet?.recentTransactions ?? []).map((transaction) => {
              const isCredit = transaction.type === 'TOP_UP' || transaction.type === 'AUTO_RECHARGE';
              const vehicle = vehicles.find((item) => item.id === transaction.vehicleId);
              return (
                <article key={transaction.id} className="transaction-row">
                  <div className={`transaction-icon ${isCredit ? 'credit' : 'debit'}`}>{isCredit ? <ArrowDownLeft size={17} /> : <ArrowUpRight size={17} />}</div>
                  <div className="transaction-copy"><strong>{transaction.type === 'AUTO_RECHARGE' ? 'Vehicle auto-recharge' : transaction.description}</strong><span>{vehicle ? `${vehicle.makeAndModel} • ` : ''}{friendlyDate(transaction.timestamp)}</span></div>
                  <b className={isCredit ? 'credit' : 'debit'}>{isCredit ? '+' : '−'}{rupees(Math.abs(transaction.amount))}</b>
                </article>
              );
            })}
            {!loading && !(wallet?.recentTransactions?.length) && <div className="wallet-empty compact"><WalletCards size={24} /><h3>No wallet activity yet</h3><p>Your transactions will appear here.</p></div>}
          </div>
          <button className="manage-vehicles" onClick={onOpenVehicles}>Manage all vehicles <ArrowUpRight size={14} /></button>
        </section>
      </div>

      {showVehicleForm && (
        <div className="vehicle-form-backdrop" role="presentation" onMouseDown={() => setShowVehicleForm(false)}>
          <section className="vehicle-form-modal" role="dialog" aria-modal="true" aria-labelledby="vehicle-form-title" onMouseDown={(event) => event.stopPropagation()}>
            <div className="wallet-panel-head"><div><h2 id="vehicle-form-title">Add your EV</h2><p>This vehicle can have its own auto-recharge rule.</p></div><button className="modal-close" onClick={() => setShowVehicleForm(false)} aria-label="Close">×</button></div>
            <div className="vehicle-form-fields">
              <label>Make and model<input value={vehicleForm.makeAndModel} onChange={(event) => setVehicleForm((current) => ({ ...current, makeAndModel: event.target.value }))} placeholder="Tata Nexon EV" autoFocus /></label>
              <label>Registration number<input value={vehicleForm.registrationNumber} onChange={(event) => setVehicleForm((current) => ({ ...current, registrationNumber: event.target.value.toUpperCase() }))} placeholder="UP32 AB 1234" /></label>
              <label>Battery capacity<input value={vehicleForm.batteryCapacity} onChange={(event) => setVehicleForm((current) => ({ ...current, batteryCapacity: event.target.value }))} placeholder="40.5 kWh" /></label>
              <label>Connector<select value={vehicleForm.connectorType} onChange={(event) => setVehicleForm((current) => ({ ...current, connectorType: event.target.value }))}><option>CCS2</option><option>Type 2</option><option>CHAdeMO</option><option>Bharat DC-001</option></select></label>
            </div>
            <div className="vehicle-form-actions"><button className="secondary-action" onClick={() => setShowVehicleForm(false)}>Cancel</button><button className="feature-primary" onClick={() => void addVehicle()} disabled={saving}>{saving ? 'Adding…' : 'Add vehicle'}</button></div>
          </section>
        </div>
      )}
    </section>
  );
}
