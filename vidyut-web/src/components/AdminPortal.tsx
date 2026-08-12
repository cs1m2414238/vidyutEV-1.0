import { useCallback, useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import {
  Activity, BadgeIndianRupee, BellRing, BookOpenCheck, Bot, Building2, CalendarClock,
  Check, ChevronRight, CircleAlert, CreditCard, FileCheck2, Gauge, LandPlot, LogOut,
  Menu, Network, PackageCheck, RefreshCw, Search, ShieldCheck, ShieldEllipsis, Store,
  UserCog, Users, Wrench, X,
} from 'lucide-react';
import { apiRequest } from '../services/api';
import './AdminPortal.css';

type Capability = 'OVERVIEW' | 'ACCOUNTS' | 'VERIFICATIONS' | 'OPERATIONS' | 'FINANCE' | 'SUPPORT' | 'AI_NETWORK' | 'AUDIT';
type AdminRole = 'SUPER_ADMIN' | 'VERIFICATION_ADMIN' | 'SUPPORT_ADMIN' | 'FINANCE_ADMIN' | 'OPERATIONS_ADMIN';
type Tab = 'overview' | 'accounts' | 'verifications' | 'operations' | 'finance' | 'revenue' | 'support' | 'network' | 'audit';

interface AdminProfile { accountId: number; email: string; displayName: string; role: AdminRole; capabilities: Capability[]; lastLoginAt?: string }
interface AdminSession { token: string; admin: AdminProfile }
interface Verification {
  id: number; companyId: number; legalName?: string; cinLlpin?: string; gstin?: string; panLast4?: string;
  representativeName?: string; representativeWorkEmail?: string; representativePhone?: string;
  bankAccountHolder?: string; bankName?: string; bankAccountLast4?: string;
  businessIdentityVerified: boolean; representativeVerified: boolean; bankVerified: boolean;
  chargerDocumentsVerified: boolean; status: string; trustLevel: string; completedLayers: number;
  missingRequirements: string[]; submittedAt?: string; rejectionReason?: string;
  incorporationDocumentUrl?: string; gstCertificateUrl?: string; authorizationProofUrl?: string;
  cancelledChequeUrl?: string; chargerCatalogueUrl?: string; complianceDocumentUrl?: string;
}
interface Snapshot {
  metrics: Record<string, number>;
  accounts: Array<Record<string, unknown>>;
  companyVerifications: Verification[];
  hostVerifications: Array<Record<string, unknown>>;
  properties: Array<Record<string, unknown>>;
  products: Array<Record<string, unknown>>;
  stations: Array<Record<string, unknown>>;
  installations: Array<Record<string, unknown>>;
  bookings: Array<Record<string, unknown>>;
  payments: Array<Record<string, unknown>>;
  autopilotTrips: Array<Record<string, unknown>>;
  networkSuggestions: Array<Record<string, unknown> & { delayMinutes?: number }>;
  announcements: Array<Record<string, unknown>>;
}
interface Audit { id: number; adminAccountId: number; action: string; resourceType: string; resourceId?: string; summary: string; createdAt: string }

const emptySnapshot: Snapshot = { metrics: {}, accounts: [], companyVerifications: [], hostVerifications: [], properties: [], products: [], stations: [], installations: [], bookings: [], payments: [], autopilotTrips: [], networkSuggestions: [], announcements: [] };
const nav: Array<{ tab: Tab; capability: Capability; label: string; icon: typeof Gauge }> = [
  { tab: 'overview', capability: 'OVERVIEW', label: 'Command center', icon: Gauge },
  { tab: 'accounts', capability: 'ACCOUNTS', label: 'Accounts & staff', icon: Users },
  { tab: 'verifications', capability: 'VERIFICATIONS', label: 'Trust & verification', icon: ShieldCheck },
  { tab: 'operations', capability: 'OPERATIONS', label: 'Network operations', icon: Wrench },
  { tab: 'finance', capability: 'FINANCE', label: 'Finance & settlements', icon: BadgeIndianRupee },
  { tab: 'revenue', capability: 'FINANCE', label: 'Revenue intelligence', icon: Activity },
  { tab: 'support', capability: 'SUPPORT', label: 'Support & notices', icon: BellRing },
  { tab: 'network', capability: 'AI_NETWORK', label: 'AI network control', icon: Network },
  { tab: 'audit', capability: 'AUDIT', label: 'Audit trail', icon: BookOpenCheck },
];

function loadAdminSession(): AdminSession | null {
  try {
    const token = localStorage.getItem('vidyut_admin_token');
    const profile = localStorage.getItem('vidyut_admin_profile');
    return token && profile ? { token, admin: JSON.parse(profile) as AdminProfile } : null;
  } catch { return null; }
}

function saveAdminSession(session: AdminSession) {
  localStorage.setItem('vidyut_admin_token', session.token);
  localStorage.setItem('vidyut_admin_profile', JSON.stringify(session.admin));
}

function clearAdminSession() {
  localStorage.removeItem('vidyut_admin_token');
  localStorage.removeItem('vidyut_admin_profile');
}

function tabFromHash(): Tab {
  const value = window.location.hash.replace('#/admin/', '');
  return (nav.some(item => item.tab === value) ? value : 'overview') as Tab;
}

export function AdminPortal() {
  const [session, setSession] = useState<AdminSession | null>(() => loadAdminSession());
  const [tab, setTab] = useState<Tab>(() => tabFromHash());
  const [snapshot, setSnapshot] = useState<Snapshot>(emptySnapshot);
  const [audits, setAudits] = useState<Audit[]>([]);
  const [loading, setLoading] = useState(Boolean(session));
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState('');

  const auth = useMemo(() => session ? { headers: { Authorization: `Bearer ${session.token}` } } : {}, [session]);
  const load = useCallback(async () => {
    if (!session) return;
    setLoading(true); setError('');
    try {
      const [data, log] = await Promise.all([
        apiRequest<Snapshot>('/admin/portal/snapshot', { method: 'GET', ...auth }),
        session.admin.capabilities.includes('AUDIT')
          ? apiRequest<Audit[]>('/admin/portal/audit', { method: 'GET', ...auth }) : Promise.resolve([]),
      ]);
      setSnapshot(data); setAudits(log);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : 'Unable to load the Admin Portal.';
      setError(message);
      if (/unauthorized|token|administrator access/i.test(message)) { clearAdminSession(); setSession(null); }
    } finally { setLoading(false); }
  }, [auth, session]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    const sync = () => setTab(tabFromHash());
    window.addEventListener('hashchange', sync);
    return () => window.removeEventListener('hashchange', sync);
  }, []);

  if (!session) return <AdminLogin onLogin={(next) => { saveAdminSession(next); setSession(next); window.location.hash = '#/admin/overview'; }} />;

  const visibleNav = nav.filter(item => session.admin.capabilities.includes(item.capability));
  const go = (next: Tab) => { setTab(next); setMenuOpen(false); window.location.hash = `#/admin/${next}`; };
  const action = async (key: string, path: string, init: RequestInit, success: string) => {
    try {
      setBusy(key); setError(''); setNotice('');
      await apiRequest(path, { ...init, ...auth, headers: { ...(auth as { headers?: Record<string,string> }).headers, ...(init.headers as Record<string,string> || {}) } });
      setNotice(success); await load();
    } catch (actionError) { setError(actionError instanceof Error ? actionError.message : 'Action failed.'); }
    finally { setBusy(''); }
  };

  return <div className="admin-shell">
    <aside className={menuOpen ? 'admin-sidebar open' : 'admin-sidebar'}>
      <div className="admin-brand"><span>V</span><div><strong>VIDYUT</strong><small>Control plane</small></div><button onClick={() => setMenuOpen(false)} aria-label="Close navigation"><X size={18}/></button></div>
      <div className="admin-scope"><ShieldEllipsis size={18}/><div><strong>{session.admin.role.replaceAll('_', ' ')}</strong><small>Least-privilege workspace</small></div></div>
      <nav>{visibleNav.map(item => <button key={item.tab} className={tab === item.tab ? 'active' : ''} onClick={() => go(item.tab)}><item.icon size={18}/><span>{item.label}</span><ChevronRight size={14}/></button>)}</nav>
      <div className="admin-user"><div>{initials(session.admin.displayName)}</div><span><strong>{session.admin.displayName}</strong><small title={session.admin.email}>{session.admin.email}</small></span><button aria-label="Log out" onClick={() => { clearAdminSession(); setSession(null); window.location.hash = '#/admin/login'; }}><LogOut size={18}/></button></div>
    </aside>
    {menuOpen && <button className="admin-scrim" onClick={() => setMenuOpen(false)} aria-label="Close navigation"/>}
    <main className="admin-main">
      <header className="admin-topbar"><button className="admin-menu" onClick={() => setMenuOpen(true)}><Menu size={19}/></button><div className="admin-search"><Search size={17}/><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search the current control view"/></div><span className="admin-live"><i/> LIVE CONTROL</span><button className="admin-refresh" onClick={() => void load()} disabled={loading}><RefreshCw size={16} className={loading ? 'spin' : ''}/>Sync</button></header>
      <div className="admin-content">
        <div className="admin-heading"><div><span>VIDYUT ADMIN · GOVERNANCE</span><h1>{nav.find(item => item.tab === tab)?.label}</h1><p>Review, intervene and audit without crossing account boundaries.</p></div><div><ShieldCheck size={18}/> Access scoped to {session.admin.role.replaceAll('_',' ').toLowerCase()}</div></div>
        {error && <div className="admin-alert error"><CircleAlert size={18}/>{error}</div>}
        {notice && <div className="admin-alert success"><Check size={18}/>{notice}</div>}
        {loading ? <AdminLoading/> : <AdminView tab={tab} snapshot={snapshot} audits={audits} search={search} admin={session.admin} busy={busy} action={action}/>} 
      </div>
    </main>
  </div>;
}

function AdminLogin({ onLogin }: { onLogin: (session: AdminSession) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setLoading(true); setError('');
    try { onLogin(await apiRequest<AdminSession>('/admin/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })); }
    catch (loginError) { setError(loginError instanceof Error ? loginError.message : 'Unable to sign in.'); }
    finally { setLoading(false); }
  };
  return <div className="admin-login-page"><section className="admin-login-story"><div className="admin-login-brand"><span>V</span><strong>VIDYUT</strong></div><div><span className="admin-kicker">NATIONAL EV NETWORK CONTROL</span><h1>One trusted view.<br/>Every critical decision.</h1><p>A separate, auditable workspace for verification, operations, support, finance and AI network governance.</p><div className="admin-story-grid"><article><ShieldCheck/><strong>Layered trust</strong><span>Verify businesses, representatives, banks, chargers, Hosts and land.</span></article><article><Network/><strong>Network intelligence</strong><span>Turn route experience into safer placement and intervention decisions.</span></article></div></div><small>Authorized Vidyut personnel only · Actions are recorded</small></section><section className="admin-login-panel"><form onSubmit={submit}><div className="admin-lock"><ShieldEllipsis size={25}/></div><span className="admin-kicker">SECURE ADMIN ACCESS</span><h2>Sign in to Control Plane</h2><p>This login is isolated from EV Owner, Host and Company accounts.</p>{error && <div className="admin-alert error"><CircleAlert size={17}/>{error}</div>}<label>Administrator email<input autoFocus type="email" value={email} onChange={event => setEmail(event.target.value)} required/></label><label>Password<div className="admin-password"><input type={show ? 'text' : 'password'} value={password} onChange={event => setPassword(event.target.value)} required/><button type="button" onClick={() => setShow(value => !value)}>{show ? 'Hide' : 'Show'}</button></div></label><button className="admin-signin" disabled={loading}>{loading ? 'Checking secure access…' : 'Enter Admin Portal'}<ChevronRight size={18}/></button><a href="#/login">Return to Vidyut app login</a><small>Local bootstrap credentials are configured server-side through VIDYUT_ADMIN_* environment variables.</small></form></section></div>;
}

function AdminView({ tab, snapshot, audits, search, admin, busy, action }: { tab: Tab; snapshot: Snapshot; audits: Audit[]; search: string; admin: AdminProfile; busy: string; action: (key:string,path:string,init:RequestInit,success:string)=>Promise<void> }) {
  if (tab === 'overview') return <Overview snapshot={snapshot}/>;
  if (tab === 'verifications') return <VerificationCenter snapshot={snapshot} busy={busy} action={action}/>;
  if (tab === 'accounts') return <AccountsView items={snapshot.accounts} search={search} admin={admin} busy={busy} action={action}/>;
  if (tab === 'operations') return <OperationsView snapshot={snapshot} search={search}/>;
  if (tab === 'finance') return <FinanceView items={snapshot.payments} busy={busy} action={action}/>;
  if (tab === 'revenue') return <RevenueView snapshot={snapshot} search={search}/>;
  if (tab === 'support') return <SupportView snapshot={snapshot} busy={busy} action={action}/>;
  if (tab === 'network') return <NetworkView snapshot={snapshot}/>;
  return <AuditView items={audits} search={search}/>;
}

function Overview({ snapshot }: { snapshot: Snapshot }) {
  const m = snapshot.metrics;
  return <><div className="admin-metrics"><Metric icon={Users} label="Accounts" value={m.accounts}/><Metric icon={Store} label="Verified ecosystem" value={(m.hosts||0)+(m.companies||0)}/><Metric icon={Network} label="Live stations" value={m.stations}/><Metric icon={CalendarClock} label="Bookings" value={m.bookings}/><Metric icon={Bot} label="Autopilot trips" value={m.autopilotTrips}/></div><div className="admin-overview-grid"><Panel title="Trust queue" subtitle="Items that need human review" icon={ShieldCheck}><div className="admin-attention"><Attention label="Company submissions" value={snapshot.companyVerifications.length} tone="amber"/><Attention label="Host identities" value={snapshot.hostVerifications.length} tone="violet"/><Attention label="Land properties" value={snapshot.properties.filter(item => item.status === 'PENDING_APPROVAL').length} tone="blue"/><Attention label="Charger products" value={snapshot.products.filter(item => item.approvalStatus === 'PENDING_REVIEW').length} tone="green"/></div></Panel><Panel title="System coverage" subtitle="Real records, no fallback metrics" icon={Activity}><div className="admin-coverage"><span><b>{m.installations||0}</b> installations</span><span><b>{m.sessions||0}</b> sessions</span><span><b>{m.payments||0}</b> payments</span><span><b>{m.routeExperiences||0}</b> route memories</span></div></Panel></div></>;
}

function VerificationCenter({ snapshot, busy, action }: { snapshot: Snapshot; busy: string; action: AdminAction }) {
  return <div className="admin-stack"><Panel title="Company verification" subtitle="Email + four mandatory review layers" icon={Building2}>{snapshot.companyVerifications.length ? snapshot.companyVerifications.map(item => <article className="admin-review-card" key={item.id}><div className="admin-review-title"><div className="admin-entity-icon"><Building2/></div><span><strong>{item.legalName || `Company #${item.companyId}`}</strong><small>{item.cinLlpin} · GST {item.gstin} · PAN •••• {item.panLast4}</small></span><Status value={item.status}/></div><div className="admin-layer-grid"><Layer label="Business identity" ok={item.businessIdentityVerified} detail={item.incorporationDocumentUrl}/><Layer label="Representative" ok={item.representativeVerified} detail={item.representativeName}/><Layer label="Bank match" ok={item.bankVerified} detail={`${item.bankName||'Bank'} •••• ${item.bankAccountLast4||'—'}`}/><Layer label="Charger compliance" ok={item.chargerDocumentsVerified} detail={item.complianceDocumentUrl}/></div><div className="admin-review-actions"><span>{item.completedLayers}/4 approved · {item.trustLevel.replaceAll('_',' ')}</span><button className="reject" disabled={Boolean(busy)} onClick={() => void action(`company-${item.id}`, `/admin/portal/companies/${item.companyId}/review`, { method:'PATCH', body: JSON.stringify({ status:'REJECTED', businessIdentityVerified:false, representativeVerified:false, bankVerified:false, chargerDocumentsVerified:false, trustLevel:'UNVERIFIED', note:'Documents require correction', rejectionReason:'One or more submitted documents could not be validated.' }) }, 'Company submission returned for correction.')}>Return</button><button disabled={Boolean(busy)} onClick={() => void action(`company-${item.id}`, `/admin/portal/companies/${item.companyId}/review`, { method:'PATCH', body: JSON.stringify({ status:'VERIFIED', businessIdentityVerified:true, representativeVerified:true, bankVerified:true, chargerDocumentsVerified:true, trustLevel:'VIDYUT_VERIFIED', note:'All four layers reviewed and approved.' }) }, 'Company is now Vidyut verified.')}>Approve all layers</button></div></article>) : <Empty text="No company submissions are waiting."/>}</Panel><div className="admin-dual"><ReviewList title="Host identity & KYC" icon={UserCog} items={snapshot.hostVerifications} nameKey="name" statusKey="status" detailKey="email" busy={busy} onReview={(item, approved) => action(`host-${item.accountId}`, `/admin/portal/hosts/${item.accountId}/review`, { method:'PATCH', body:JSON.stringify({approved,note:approved?'Identity evidence approved.':'Identity evidence requires correction.'}) }, `Host ${approved?'approved':'rejected'}.`)}/><ReviewList title="Land ownership" icon={LandPlot} items={snapshot.properties.filter(item => item.status === 'PENDING_APPROVAL')} nameKey="title" statusKey="status" detailKey="address" busy={busy} onReview={(item,approved)=>action(`property-${item.id}`,`/admin/portal/properties/${item.id}/review`,{method:'PATCH',body:JSON.stringify({approved,note:approved?'Ownership evidence approved.':'Ownership evidence requires correction.'})},`Property ${approved?'published':'rejected'}.`)}/></div><ReviewList title="Charger catalogue compliance" icon={PackageCheck} items={snapshot.products.filter(item => item.approvalStatus !== 'APPROVED')} nameKey="model" statusKey="approvalStatus" detailKey="company" busy={busy} onReview={(item,approved)=>action(`product-${item.id}`,`/admin/portal/products/${item.id}/review`,{method:'PATCH',body:JSON.stringify({approved,note:approved?'Compliance document approved.':'Compliance evidence requires correction.'})},`Product ${approved?'published':'rejected'}.`)}/></div>;
}

type AdminAction = (key:string,path:string,init:RequestInit,success:string)=>Promise<void>;

function AccountsView({ items, search, admin, busy, action }: { items:Array<Record<string,unknown>>;search:string;admin:AdminProfile;busy:string;action:AdminAction }) {
  const [form,setForm]=useState({email:'',password:'',displayName:'',role:'SUPPORT_ADMIN'});
  const filtered=filterRows(items,search);
  return <div className="admin-stack">{admin.role==='SUPER_ADMIN'&&<Panel title="Create scoped staff account" subtitle="Admins are separate identities with fixed module access" icon={UserCog}><form className="admin-inline-form" onSubmit={event=>{event.preventDefault();void action('create-admin','/admin/portal/admins',{method:'POST',body:JSON.stringify(form)},'Administrator account created.')}}><input placeholder="Full name" value={form.displayName} onChange={e=>setForm({...form,displayName:e.target.value})} required/><input placeholder="Work email" type="email" value={form.email} onChange={e=>setForm({...form,email:e.target.value})} required/><input placeholder="Temporary password (12+ chars)" type="password" minLength={12} value={form.password} onChange={e=>setForm({...form,password:e.target.value})} required/><select value={form.role} onChange={e=>setForm({...form,role:e.target.value})}><option>VERIFICATION_ADMIN</option><option>SUPPORT_ADMIN</option><option>FINANCE_ADMIN</option><option>OPERATIONS_ADMIN</option><option>SUPER_ADMIN</option></select><button disabled={Boolean(busy)}>Create staff account</button></form></Panel>}<Panel title="Platform accounts" subtitle="Activate or suspend access without deleting history" icon={Users}><DataTable headers={['Account','Partition','Roles','Trust','Access']} rows={filtered.map(item=>[<div><strong>{str(item.email)}</strong><small>#{str(item.id)}</small></div>,str(item.accountType),str(item.roles),str(item.emailVerified?'Email verified':'Email pending'),<button className={item.enabled?'table-danger':''} disabled={Boolean(busy)} onClick={()=>void action(`account-${item.id}`,`/admin/portal/accounts/${item.id}/enabled?enabled=${!item.enabled}`,{method:'PATCH'},item.enabled?'Account suspended.':'Account activated.')}>{item.enabled?'Suspend':'Activate'}</button>])}/></Panel></div>;
}

function OperationsView({ snapshot, search }: { snapshot:Snapshot;search:string }) { return <div className="admin-stack"><Panel title="Stations & charger health" subtitle="National network status and ownership" icon={Wrench}><DataTable headers={['Station','City','Status','Availability','Load']} rows={filterRows(snapshot.stations,search).map(i=>[str(i.name),str(i.city),<Status value={str(i.status)}/>,str(i.availability),`${str(i.occupancy)}% · queue ${str(i.queue)}`])}/></Panel><Panel title="Installation pipeline" subtitle="Host ↔ Company deployments through commissioning" icon={LandPlot}><DataTable headers={['Property','Company','Product','Stage','Station']} rows={snapshot.installations.map(i=>[str(i.property),str(i.company),str(i.product),<Status value={str(i.status)}/>,i.stationId?`#${str(i.stationId)}`:'Not commissioned'])}/></Panel></div> }

function FinanceView({ items,busy,action }: {items:Array<Record<string,unknown>>;busy:string;action:AdminAction}) { return <Panel title="Payments & refunds" subtitle="Refunds are consequential and recorded in the audit trail" icon={CreditCard}><DataTable headers={['Payment','Booking','Amount','Transaction','Status','Action']} rows={items.map(i=>[`#${str(i.id)}`,`#${str(i.bookingId)}`,money(Number(i.amount)),str(i.transaction),<Status value={str(i.status)}/>,i.status==='SUCCESS'?<button disabled={Boolean(busy)} onClick={()=>void action(`refund-${i.id}`,`/admin/portal/payments/${i.id}/refund`,{method:'PATCH',body:JSON.stringify({note:'Admin-approved refund'})},'Payment refunded.')}>Refund</button>:'—'])}/></Panel> }

function RevenueView({ snapshot, search }: { snapshot: Snapshot; search: string }) {
  const payments = snapshot.payments;
  const paymentStatus = (item: Record<string, unknown>) => str(item.status).toUpperCase();
  const sum = (items: Array<Record<string, unknown>>) => items.reduce((total, item) => total + (Number(item.amount) || 0), 0);
  const successful = payments.filter(item => paymentStatus(item) === 'SUCCESS');
  const refunded = payments.filter(item => paymentStatus(item) === 'REFUNDED');
  const captured = payments.filter(item => ['SUCCESS', 'REFUNDED'].includes(paymentStatus(item)));
  const decided = payments.filter(item => ['SUCCESS', 'REFUNDED', 'FAILED'].includes(paymentStatus(item)));
  const activeBookings = snapshot.bookings.filter(item => ['PENDING', 'CONFIRMED', 'IN_PROGRESS'].includes(str(item.status).toUpperCase()));
  const grossCollections = sum(captured);
  const refunds = sum(refunded);
  const netCollections = sum(successful);
  const confirmedPipeline = sum(activeBookings);
  const pendingPayments = sum(payments.filter(item => paymentStatus(item) === 'PENDING'));
  const averageTransaction = successful.length ? netCollections / successful.length : 0;
  const captureRate = decided.length ? (captured.length / decided.length) * 100 : 0;

  const today = new Date();
  const days = Array.from({ length: 7 }, (_, index) => {
    const value = new Date(today);
    value.setHours(0, 0, 0, 0);
    value.setDate(today.getDate() - (6 - index));
    const key = dayKey(value);
    const amount = sum(successful.filter(item => dayKey(new Date(str(item.timestamp))) === key));
    return { key, label: value.toLocaleDateString('en-IN', { weekday: 'short' }), amount };
  });
  const dailyPeak = Math.max(0, ...days.map(item => item.amount));
  const dailyScale = Math.max(1, dailyPeak);

  const outcomes = ['SUCCESS', 'REFUNDED', 'PENDING', 'FAILED'].map(status => {
    const count = payments.filter(item => paymentStatus(item) === status).length;
    return { status, count, share: payments.length ? (count / payments.length) * 100 : 0 };
  });

  const stationRevenue = new Map<string, { amount: number; transactions: number }>();
  successful.forEach(item => {
    const station = str(item.station) || 'Unlinked payment';
    const current = stationRevenue.get(station) || { amount: 0, transactions: 0 };
    stationRevenue.set(station, { amount: current.amount + (Number(item.amount) || 0), transactions: current.transactions + 1 });
  });
  const stationRankings = Array.from(stationRevenue.entries())
    .map(([station, value]) => ({ station, ...value }))
    .sort((a, b) => b.amount - a.amount);
  const topStations = stationRankings.slice(0, 5);
  const topStationAmount = Math.max(1, ...topStations.map(item => item.amount));
  const chartColors = ['#0a9169', '#35c994', '#3d83df', '#8b69d6', '#e3a13a'];
  const leadingSources = stationRankings.slice(0, 4);
  const otherRevenue = stationRankings.slice(4).reduce((total, item) => total + item.amount, 0);
  const revenueSources = [
    ...leadingSources.map((item, index) => ({ label: item.station, value: item.amount, color: chartColors[index] })),
    ...(otherRevenue ? [{ label: 'Other stations', value: otherRevenue, color: chartColors[4] }] : []),
  ];
  const outcomeSegments = outcomes.map((item, index) => ({ ...item, label: item.status.replaceAll('_', ' '), value: item.count, color: chartColors[index] }));

  const pipelineByStation = new Map<string, { amount: number; bookings: number }>();
  activeBookings.forEach(item => {
    const station = str(item.station) || 'Unassigned station';
    const current = pipelineByStation.get(station) || { amount: 0, bookings: 0 };
    pipelineByStation.set(station, { amount: current.amount + (Number(item.amount) || 0), bookings: current.bookings + 1 });
  });
  const opportunities = Array.from(pipelineByStation.entries())
    .map(([station, value]) => ({ station, ...value, collected: stationRevenue.get(station)?.amount || 0 }))
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 5);
  const topOpportunity = Math.max(1, ...opportunities.map(item => item.amount));

  const ledger = filterRows(payments, search)
    .slice()
    .sort((a, b) => new Date(str(b.timestamp)).getTime() - new Date(str(a.timestamp)).getTime())
    .slice(0, 20);

  return <div className="admin-stack admin-revenue">
    <section className="admin-revenue-hero">
      <div className="admin-revenue-hero-copy">
        <span><Activity size={14}/> LIVE COLLECTION SIGNAL</span>
        <h2>Know what the network earned, retained and expects next.</h2>
        <p>Collections are calculated directly from payment and booking records. No assumed commission or synthetic forecast is included.</p>
      </div>
      <div className="admin-revenue-hero-facts">
        <div><small>Captured transactions</small><strong>{captured.length}</strong><span>{payments.length} total payment attempts</span></div>
        <div><small>Confirmed pipeline</small><strong>{money(confirmedPipeline)}</strong><span>{activeBookings.length} active bookings</span></div>
      </div>
    </section>

    <div className="admin-revenue-kpis">
      <RevenueKpi icon={BadgeIndianRupee} label="Gross collections" value={money(grossCollections)} detail="Successful + later-refunded captures" tone="green"/>
      <RevenueKpi icon={Activity} label="Net collections" value={money(netCollections)} detail="Successful payments retained" tone="blue"/>
      <RevenueKpi icon={RefreshCw} label="Refunded value" value={money(refunds)} detail={`${refunded.length} refunded transaction${refunded.length === 1 ? '' : 's'}`} tone="amber"/>
      <RevenueKpi icon={CreditCard} label="Capture rate" value={`${captureRate.toFixed(1)}%`} detail={`${captured.length} of ${decided.length} decided attempts`} tone="violet"/>
    </div>

    <div className="admin-revenue-grid">
      <Panel title="Net collections · last 7 days" subtitle="Daily value from successful payments" icon={Activity}>
        <div className="admin-revenue-chart">
          <div className="admin-revenue-axis"><span>{money(dailyPeak)}</span><span>{money(dailyPeak / 2)}</span><span>{money(0)}</span></div>
          <div className="admin-revenue-bars">{days.map(item => <div className="admin-revenue-bar" key={item.key} title={`${item.label}: ${money(item.amount)}`}><strong>{item.amount ? money(item.amount) : ''}</strong><div><i style={{ height: `${item.amount ? Math.max(8, (item.amount / dailyScale) * 100) : 2}%` }}/></div><span>{item.label}</span></div>)}</div>
        </div>
      </Panel>
      <Panel title="Where revenue comes from" subtitle="Share of retained collections by station" icon={Store}>
        <DonutChart segments={revenueSources} centerValue={money(netCollections)} centerLabel="net collected" formatValue={money} emptyText="Successful payments will reveal the station revenue mix."/>
      </Panel>
    </div>

    <div className="admin-revenue-grid secondary">
      <Panel title="Where revenue can grow" subtitle="Opportunity ranked by real active-booking value" icon={LandPlot}>
        {opportunities.length ? <div className="admin-opportunity-list">{opportunities.map((item, index) => {
          const signal = item.collected === 0 ? 'Untapped demand' : item.amount > item.collected * .5 ? 'High near-term demand' : 'Repeat demand';
          return <article key={item.station}><div className="admin-opportunity-rank">0{index + 1}</div><div className="admin-opportunity-copy"><div><span><strong>{item.station}</strong><small>{item.bookings} active booking{item.bookings === 1 ? '' : 's'} · {money(item.amount)} pipeline</small></span><em>{signal}</em></div><i><b style={{ width: `${(item.amount / topOpportunity) * 100}%` }}/></i><p>{item.collected ? `${money(item.collected)} already collected here; convert the active demand and protect availability.` : 'Active demand exists without recorded successful collections; prioritize payment conversion and charger availability.'}</p></div></article>;
        })}</div> : <Empty text="Active bookings will identify the next revenue opportunities."/>}
      </Panel>
      <Panel title="Payment outcome mix" subtitle="Conversion and collection risk by attempt" icon={CreditCard}>
        <DonutChart segments={outcomeSegments} centerValue={`${payments.length}`} centerLabel="payment attempts" formatValue={value => `${value}`} emptyText="Payment outcomes will appear after the first attempt."/>
      </Panel>
    </div>

    <div className="admin-revenue-grid secondary">
      <Panel title="Top earning stations" subtitle="Ranked by retained successful collections" icon={Store}>
        {topStations.length ? <div className="admin-station-revenue">{topStations.map((item, index) => <article key={item.station}><b>{index + 1}</b><div><span><strong>{item.station}</strong><small>{item.transactions} transaction{item.transactions === 1 ? '' : 's'}</small></span><em>{money(item.amount)}</em><i><u style={{ width: `${(item.amount / topStationAmount) * 100}%` }}/></i></div></article>)}</div> : <Empty text="Station earnings will appear after the first successful payment."/>}
      </Panel>
      <Panel title="Collection health" subtitle="Operational indicators behind the headline value" icon={Gauge}>
        <div className="admin-revenue-brief">
          <article><span>Active booking pipeline<small>Pending, confirmed and in-progress bookings</small></span><strong>{money(confirmedPipeline)}</strong></article>
          <article><span>Pending payment value<small>Started attempts not yet resolved</small></span><strong>{money(pendingPayments)}</strong></article>
          <article><span>Average successful transaction<small>Net collections per successful payment</small></span><strong>{money(averageTransaction)}</strong></article>
          <article><span>Refund exposure<small>Share of gross captured value returned</small></span><strong>{grossCollections ? `${((refunds / grossCollections) * 100).toFixed(1)}%` : '0.0%'}</strong></article>
        </div>
      </Panel>
    </div>

    <Panel title="Revenue ledger" subtitle="Latest payment records; use the global search to filter this table" icon={BookOpenCheck}>
      <DataTable headers={['Time','Payment','Station','Booking','Amount','Status']} rows={ledger.map(item => [date(str(item.timestamp)), `#${str(item.id)}`, str(item.station) || 'Unlinked payment', `#${str(item.bookingId)}`, money(Number(item.amount)), <Status value={str(item.status)}/>])}/>
    </Panel>
  </div>;
}

function DonutChart({ segments, centerValue, centerLabel, formatValue, emptyText }: { segments: Array<{ label: string; value: number; color: string }>; centerValue: string; centerLabel: string; formatValue: (value: number) => string; emptyText: string }) {
  const visible = segments.filter(item => item.value > 0);
  const total = visible.reduce((sum, item) => sum + item.value, 0);
  let offset = 0;
  const arcs = visible.map(item => {
    const share = total ? (item.value / total) * 100 : 0;
    const arc = { ...item, share, offset };
    offset += share;
    return arc;
  });
  return <div className="admin-donut-layout">
    <div className="admin-donut">
      <svg viewBox="0 0 120 120" role="img" aria-label={`${centerLabel}: ${centerValue}`}>
        <circle className="admin-donut-track" cx="60" cy="60" r="45" pathLength="100"/>
        {arcs.map(item => <circle key={item.label} className="admin-donut-arc" cx="60" cy="60" r="45" pathLength="100" stroke={item.color} strokeDasharray={`${item.share} ${100 - item.share}`} strokeDashoffset={-item.offset}/>) }
      </svg>
      <div><strong>{centerValue}</strong><span>{centerLabel}</span></div>
    </div>
    <div className="admin-donut-legend">{arcs.length ? arcs.map(item => <article key={item.label}><i style={{ background: item.color }}/><span><strong>{item.label}</strong><small>{formatValue(item.value)}</small></span><em>{item.share.toFixed(0)}%</em></article>) : <p><CircleAlert size={17}/>{emptyText}</p>}</div>
  </div>;
}

function RevenueKpi({ icon: Icon, label, value, detail, tone }: { icon: typeof Gauge; label: string; value: string; detail: string; tone: string }) {
  return <article className={`admin-revenue-kpi ${tone}`}><span><Icon size={19}/></span><div><small>{label}</small><strong>{value}</strong><p>{detail}</p></div></article>;
}

function SupportView({ snapshot,busy,action }: {snapshot:Snapshot;busy:string;action:AdminAction}) { const [form,setForm]=useState({title:'',message:'',audience:'ALL',severity:'INFO'});return <div className="admin-stack"><Panel title="Publish platform notice" subtitle="Target an account partition without exposing private contact details" icon={BellRing}><form className="admin-inline-form notice" onSubmit={e=>{e.preventDefault();void action('announcement','/admin/portal/announcements',{method:'POST',body:JSON.stringify(form)},'Announcement published.')}}><input placeholder="Notice title" value={form.title} onChange={e=>setForm({...form,title:e.target.value})} required/><input placeholder="Message" value={form.message} onChange={e=>setForm({...form,message:e.target.value})} required/><select value={form.audience} onChange={e=>setForm({...form,audience:e.target.value})}><option>ALL</option><option>EV_OWNER</option><option>HOST</option><option>COMPANY</option></select><select value={form.severity} onChange={e=>setForm({...form,severity:e.target.value})}><option>INFO</option><option>WARNING</option><option>CRITICAL</option></select><button disabled={Boolean(busy)}>Publish</button></form></Panel><Panel title="Booking interventions" subtitle="Support can cancel active reservations; records remain intact" icon={CalendarClock}><DataTable headers={['Booking','Station','User','Start','Status','Action']} rows={snapshot.bookings.map(i=>[`#${str(i.id)}`,str(i.station),`#${str(i.userId)}`,date(str(i.startTime)),<Status value={str(i.status)}/>,!['COMPLETED','CANCELLED'].includes(str(i.status))?<button className="table-danger" disabled={Boolean(busy)} onClick={()=>void action(`booking-${i.id}`,`/admin/portal/bookings/${i.id}/cancel`,{method:'PATCH',body:JSON.stringify({note:'Cancelled by support'})},'Booking cancelled.')}>Cancel</button>:'—'])}/></Panel></div> }

function NetworkView({ snapshot }: {snapshot:Snapshot}) { return <div className="admin-stack"><div className="admin-metrics"><Metric icon={Bot} label="Trips monitored" value={snapshot.autopilotTrips.length}/><Metric icon={Network} label="Route memories" value={snapshot.networkSuggestions.length}/><Metric icon={CircleAlert} label="Negative events" value={snapshot.networkSuggestions.filter(i=>!['POSITIVE','SUCCESS'].includes(str(i.outcome))).length}/></div><Panel title="Autopilot supervision" subtitle="Mode, constraints and learned context without exposing credentials" icon={Bot}><DataTable headers={['Trip','Route','Purpose','Autonomy','Status','Cost']} rows={snapshot.autopilotTrips.map(i=>[`#${str(i.id)}`,`${str(i.origin)} → ${str(i.destination)}`,str(i.purpose),str(i.mode),<Status value={str(i.status)}/>,money(Number(i.cost))])}/></Panel><Panel title="Route experience memory" subtitle="Past outcomes guide placement and future trip decisions" icon={Network}><div className="admin-memory-list">{snapshot.networkSuggestions.map(i=><article key={str(i.id)}><span><Network size={17}/></span><div><strong>{str(i.route)}</strong><p>{str(i.detail)||'No narrative supplied.'}</p><small>{str(i.outcome)} · station {str(i.stationId)||'route-wide'} · {date(str(i.createdAt))}</small></div>{i.delayMinutes&&<b>+{str(i.delayMinutes)} min</b>}</article>)}{!snapshot.networkSuggestions.length&&<Empty text="Route experiences will appear after Autopilot trips."/>}</div></Panel></div> }

function AuditView({items,search}:{items:Audit[];search:string}) { const filtered=items.filter(i=>JSON.stringify(i).toLowerCase().includes(search.toLowerCase()));return <Panel title="Immutable admin activity" subtitle="Who changed what, where and when" icon={FileCheck2}><DataTable headers={['Time','Admin','Action','Resource','Summary']} rows={filtered.map(i=>[date(i.createdAt),`#${i.adminAccountId}`,i.action.replaceAll('_',' '),`${i.resourceType} ${i.resourceId||''}`,i.summary])}/></Panel> }

function ReviewList({title,icon:Icon,items,nameKey,statusKey,detailKey,busy,onReview}:{title:string;icon:typeof Gauge;items:Array<Record<string,unknown>>;nameKey:string;statusKey:string;detailKey:string;busy:string;onReview:(item:Record<string,unknown>,approved:boolean)=>Promise<void>}) { return <Panel title={title} subtitle="Evidence remains private until a trust decision" icon={Icon}>{items.length?<div className="admin-simple-list">{items.map((item,index)=><article key={str(item.id??item.accountId??index)}><div><strong>{str(item[nameKey])}</strong><span>{str(item[detailKey])}</span></div><Status value={str(item[statusKey])}/><section><button className="reject" disabled={Boolean(busy)} onClick={()=>void onReview(item,false)}>Reject</button><button disabled={Boolean(busy)} onClick={()=>void onReview(item,true)}>Approve</button></section></article>)}</div>:<Empty text="No records are waiting."/>}</Panel> }
function Panel({title,subtitle,icon:Icon,children}:{title:string;subtitle:string;icon:typeof Gauge;children:React.ReactNode}) { return <section className="admin-panel"><header><span><Icon size={19}/></span><div><h2>{title}</h2><p>{subtitle}</p></div></header>{children}</section> }
function DataTable({headers,rows}:{headers:string[];rows:React.ReactNode[][]}) { return rows.length?<div className="admin-table-wrap"><table><thead><tr>{headers.map(h=><th key={h}>{h}</th>)}</tr></thead><tbody>{rows.map((row,i)=><tr key={i}>{row.map((cell,j)=><td key={j}>{cell}</td>)}</tr>)}</tbody></table></div>:<Empty text="No records in this view."/> }
function Metric({icon:Icon,label,value}:{icon:typeof Gauge;label:string;value?:number}) { return <article><span><Icon size={20}/></span><div><strong>{value??0}</strong><small>{label}</small></div></article> }
function Attention({label,value,tone}:{label:string;value:number;tone:string}) { return <div className={tone}><span>{label}</span><strong>{value}</strong></div> }
function Layer({label,ok,detail}:{label:string;ok:boolean;detail?:string}) { return <div className={ok?'ok':''}><span>{ok?<Check size={15}/>:<CircleAlert size={15}/>}</span><div><strong>{label}</strong><small>{detail?'Evidence attached':'Awaiting review'}</small></div></div> }
function Status({value}:{value:string}) { return <i className={`admin-status ${statusTone(value)}`}>{value.replaceAll('_',' ')}</i> }
function Empty({text}:{text:string}) { return <div className="admin-empty"><FileCheck2 size={24}/><span>{text}</span></div> }
function AdminLoading() { return <div className="admin-loading"><span/><span/><span/><p>Synchronizing protected control data…</p></div> }
function statusTone(value:string){if(/VERIFIED|APPROVED|ACTIVE|SUCCESS|COMPLETED|ONLINE/.test(value))return'ok';if(/REJECTED|SUSPENDED|FAILED|CANCELLED|FAULT/.test(value))return'bad';return'pending'}
function initials(value:string){return value.split(/\s+/).slice(0,2).map(v=>v[0]).join('').toUpperCase()}
function str(value:unknown){if(value==null)return'';if(Array.isArray(value))return value.join(', ');return String(value)}
function date(value:string){return value?new Date(value).toLocaleString('en-IN',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'}):'—'}
function money(value:number){return new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}).format(value||0)}
function dayKey(value:Date){return Number.isNaN(value.getTime())?'':`${value.getFullYear()}-${String(value.getMonth()+1).padStart(2,'0')}-${String(value.getDate()).padStart(2,'0')}`}
function filterRows(items:Array<Record<string,unknown>>,search:string){const q=search.trim().toLowerCase();return q?items.filter(i=>JSON.stringify(i).toLowerCase().includes(q)):items}
