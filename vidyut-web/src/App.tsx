import { useState, useEffect } from 'react';
import { Building2, CarFront, HousePlug } from 'lucide-react';
import { Sidebar } from './components/Sidebar';
import { TopBar } from './components/TopBar';
import { MapView } from './components/MapView';
import { ChargerDetailModal } from './components/ChargerDetailModal';
import { BookingsView } from './components/BookingsView';
import { FeatureView } from './components/FeatureView';
import { WalletView } from './components/WalletView';
import { CompanyWorkspace } from './components/CompanyWorkspace';
import { HostWorkspace } from './components/HostWorkspace';
import { ModeSelection } from './components/ModeSelection';
import { mockUser, mockChargers, mockBookings } from './data/mockData';
import type { Charger, BookingItem, User } from './types';
import LoginPage from './components/LoginPage';
import SplashScreen from './components/SplashScreen';
import LandingPage from './components/LandingPage';
import VidyutRegisterPage from './components/RegisterPage';
import {
  clearAuthSession,
  loadAuthSession,
  saveAuthSession,
  switchAuthMode,
} from './services/api';
import type { AccessMode, AuthData } from './services/api';
import { EVOwnerDashboard } from './components/dashboards/EVOwnerDashboard';
import './components/dashboards/Dashboards.css';
import './components/DashboardShell.css';

type AppView = 'splash' | 'landing' | 'login' | 'register' | 'mode-select' | 'dashboard';
type UserRole = 'EV_OWNER' | 'LANDOWNER' | 'COMPANY_ADMIN';

function roleForMode(mode: AccessMode): UserRole {
  if (mode === 'HOST') return 'LANDOWNER';
  if (mode === 'COMPANY') return 'COMPANY_ADMIN';
  return 'EV_OWNER';
}

function userFromAuth(auth: AuthData, fallback: User = mockUser): User {
  return {
    ...fallback,
    id: String(auth.user.id),
    name: auth.user.fullName,
    email: auth.user.email,
    phone: auth.user.phone || fallback.phone,
  };
}

function getHashForState(view: AppView, tab: string): string {
  if (view === 'login') return '#/login';
  if (view === 'register') return '#/register';
  if (view === 'mode-select') return '#/mode-select';
  if (view === 'landing' || view === 'splash') return '#/landing';
  if (view === 'dashboard') {
    return tab === 'dashboard' ? '#/dashboard' : `#/dashboard/${tab}`;
  }
  return '#/';
}

function parseStateFromHash(hash: string): { view: AppView; tab: string } {
  const clean = hash.replace(/^#\/?/, '').trim();
  if (clean === 'login') return { view: 'login', tab: 'dashboard' };
  if (clean === 'register') return { view: 'register', tab: 'dashboard' };
  if (clean === 'mode-select') return { view: 'mode-select', tab: 'dashboard' };
  if (clean === 'landing' || clean === '') return { view: 'landing', tab: 'dashboard' };
  if (clean.startsWith('dashboard')) {
    const parts = clean.split('/');
    const tab = parts[1] || 'dashboard';
    return { view: 'dashboard', tab };
  }
  return { view: 'landing', tab: 'dashboard' };
}

export function App() {
  const [restoredSession] = useState<AuthData | null>(() => loadAuthSession());
  const [currentView, setCurrentView] = useState<AppView>(() => {
    if (window.location.hash) {
      return parseStateFromHash(window.location.hash).view;
    }
    if (restoredSession) return 'dashboard';
    return sessionStorage.getItem('vidyut_splash_shown') === 'true' ? 'landing' : 'splash';
  });
  const [activeTab, setActiveTab] = useState(() => {
    if (window.location.hash) {
      return parseStateFromHash(window.location.hash).tab;
    }
    return 'dashboard';
  });
  const [selectedCharger, setSelectedCharger] = useState<Charger | null>(null);
  const [mapFilter, setMapFilter] = useState<'all' | 'available' | 'fast'>('available');
  const [user, setUser] = useState<User>(() => restoredSession ? userFromAuth(restoredSession) : mockUser);
  const [isLoggedIn, setIsLoggedIn] = useState(Boolean(restoredSession));
  const [bookings, setBookings] = useState<BookingItem[]>(mockBookings);
  const [companyCounts, setCompanyCounts] = useState({ bookings: 0, notifications: 0 });
  const [hostCounts, setHostCounts] = useState({ bookings: 0, notifications: 0 });
  const [modalCharger, setModalCharger] = useState<Charger | null>(null);
  const [activeMode, setActiveMode] = useState<AccessMode>(() => restoredSession?.activeMode || 'EV_USER');
  const [activeDashboardRole, setActiveDashboardRole] = useState<UserRole>(() => roleForMode(restoredSession?.activeMode || 'EV_USER'));
  const [authToken, setAuthToken] = useState(restoredSession?.token || '');
  const [allowedModes, setAllowedModes] = useState<AccessMode[]>(() => restoredSession?.user.allowedModes || []);
  const [modeError, setModeError] = useState('');
  const [loadingMode, setLoadingMode] = useState<AccessMode | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Sync state with browser URL history stack
  const navigateToState = (newView: AppView, newTab: string = activeTab, replace: boolean = false) => {
    const newHash = getHashForState(newView, newTab);
    if (window.location.hash !== newHash) {
      if (replace) {
        window.history.replaceState(null, '', newHash);
      } else {
        window.history.pushState(null, '', newHash);
      }
    }
    setCurrentView(newView);
    setActiveTab(newTab);
  };

  // Browser Back/Forward button listener
  useEffect(() => {
    const handlePopState = () => {
      const parsed = parseStateFromHash(window.location.hash);
      setCurrentView(parsed.view);
      setActiveTab(parsed.tab);
    };

    window.addEventListener('popstate', handlePopState);
    window.addEventListener('hashchange', handlePopState);

    // Ensure URL hash is synced on initial mount
    const initialHash = getHashForState(currentView, activeTab);
    if (!window.location.hash && initialHash !== '#/') {
      window.history.replaceState(null, '', initialHash);
    }

    return () => {
      window.removeEventListener('popstate', handlePopState);
      window.removeEventListener('hashchange', handlePopState);
    };
  }, [activeTab, currentView]);

  const applyAuthenticatedSession = (auth: AuthData, promptForMode: boolean) => {
    const modes = auth.user.allowedModes?.length ? auth.user.allowedModes : [auth.user.defaultMode || auth.activeMode];
    saveAuthSession(auth);
    setUser((current) => userFromAuth(auth, current));
    setAllowedModes(modes);
    setAuthToken(auth.token);
    setActiveMode(auth.activeMode);
    setActiveDashboardRole(roleForMode(auth.activeMode));
    setModeError('');
    setIsLoggedIn(true);
    const targetView: AppView = promptForMode && modes.length > 1 ? 'mode-select' : 'dashboard';
    navigateToState(targetView, 'dashboard');
  };

  const handleSplashComplete = () => {
    sessionStorage.setItem('vidyut_splash_shown', 'true');
    navigateToState('landing', 'dashboard', true);
  };

  const handleSelectCharger = (charger: Charger) => {
    setSelectedCharger(charger);
    setModalCharger(charger);
  };

  const handleConfirmBooking = (charger: Charger, duration: number) => {
    const energy = charger.powerKw * (duration / 60);
    const cost = Number((charger.pricePerKwh * energy).toFixed(2));
    const newBooking: BookingItem = {
      id: `BK-${Math.floor(1000 + Math.random() * 9000)}`,
      chargerId: charger.id,
      chargerName: charger.name,
      address: charger.address,
      startTime: new Date().toISOString(),
      durationMinutes: duration,
      totalCost: cost,
      status: 'CONFIRMED',
      energyDelivered: Number(energy.toFixed(1)),
    };

    setBookings((current) => [newBooking, ...current]);
    setUser((current) => ({
      ...current,
      totalBookings: current.totalBookings + 1,
      walletBalance: current.walletBalance - cost,
      totalEnergyKwh: current.totalEnergyKwh + (newBooking.energyDelivered || 0),
    }));
  };

  const switchMode = async (mode: AccessMode, closeChooser = false) => {
    if (!authToken || loadingMode) return;
    if (mode === activeMode) {
      if (closeChooser) navigateToState('dashboard', activeTab);
      return;
    }

    try {
      setLoadingMode(mode);
      setModeError('');
      const auth = await switchAuthMode(mode, authToken);
      applyAuthenticatedSession(auth, false);
    } catch (error) {
      setModeError(error instanceof Error ? error.message : 'Unable to switch workspace.');
    } finally {
      setLoadingMode(null);
    }
  };

  const openLogin = (destination: 'dashboard' | 'find') => {
    navigateToState('login', destination);
  };

  const handleLogout = () => {
    clearAuthSession();
    setAuthToken('');
    setAllowedModes([]);
    setIsLoggedIn(false);
    setSidebarOpen(false);
    navigateToState('landing', 'dashboard', true);
  };

  if (currentView === 'splash') {
    return <SplashScreen onComplete={handleSplashComplete} />;
  }

  if (!isLoggedIn) {
    if (currentView === 'landing') {
      return (
        <LandingPage
          onLogin={() => openLogin('dashboard')}
          onRegister={() => navigateToState('register')}
          onExploreChargers={() => openLogin('find')}
        />
      );
    }

    if (currentView === 'register') {
      return (
        <div>
          <button className="auth-back-button" onClick={() => navigateToState('landing')}>← Back to home</button>
          <VidyutRegisterPage
            onRegistered={(auth) => applyAuthenticatedSession(auth, false)}
            onLogin={() => openLogin('dashboard')}
          />
        </div>
      );
    }

    return (
      <LoginPage
        onLogin={(auth) => applyAuthenticatedSession(auth, true)}
        onBack={() => navigateToState('landing')}
        onRegister={() => navigateToState('register')}
      />
    );
  }

  if (currentView === 'mode-select') {
    return (
      <ModeSelection
        name={user.name.split(' ')[0] || user.name}
        modes={allowedModes}
        loadingMode={loadingMode}
        error={modeError}
        onSelect={(mode) => void switchMode(mode, true)}
        onLogout={handleLogout}
      />
    );
  }

  const showOwnerBookings = activeDashboardRole === 'EV_OWNER' && activeTab === 'bookings';
  const showOwnerWallet = activeDashboardRole === 'EV_OWNER' && activeTab === 'wallet';
  const showCompanyWorkspace = activeDashboardRole === 'COMPANY_ADMIN';
  const showHostWorkspace = activeDashboardRole === 'LANDOWNER';
  const showModeFeature = activeTab !== 'dashboard' && activeTab !== 'find' && !showOwnerBookings && !showOwnerWallet && !showCompanyWorkspace && !showHostWorkspace;

  return (
    <div className="app-shell">
      <Sidebar
        active={activeTab}
        onNav={(tab) => navigateToState('dashboard', tab)}
        user={user}
        bookingCount={activeDashboardRole === 'COMPANY_ADMIN' ? companyCounts.bookings : activeDashboardRole === 'LANDOWNER' ? hostCounts.bookings : bookings.length}
        onLogout={handleLogout}
        role={activeDashboardRole}
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      <main className="app-main">
        <TopBar notificationCount={activeDashboardRole === 'COMPANY_ADMIN' ? companyCounts.notifications : activeDashboardRole === 'LANDOWNER' ? hostCounts.notifications : 3} onOpenMenu={() => setSidebarOpen(true)} />

        <div className="mode-bar">
          <span className="mode-label">{allowedModes.length > 1 ? 'Current workspace' : 'Account workspace'}</span>
          <div className="mode-tabs" aria-label="Authorized workspaces">
            {allowedModes.includes('EV_USER') && (
              <button className={`mode-tab ${activeMode === 'EV_USER' ? 'active' : ''}`} onClick={() => void switchMode('EV_USER')} disabled={Boolean(loadingMode)}>
                <CarFront size={14} /> EV Owner
              </button>
            )}
            {allowedModes.includes('HOST') && (
              <button className={`mode-tab ${activeMode === 'HOST' ? 'active' : ''}`} onClick={() => void switchMode('HOST')} disabled={Boolean(loadingMode)}>
                <HousePlug size={14} /> Charger Host
              </button>
            )}
            {allowedModes.includes('COMPANY') && (
              <button className={`mode-tab ${activeMode === 'COMPANY' ? 'active' : ''}`} onClick={() => void switchMode('COMPANY')} disabled={Boolean(loadingMode)}>
                <Building2 size={14} /> Company
              </button>
            )}
          </div>
          {modeError && <span className="mode-error" role="alert">{modeError}</span>}
        </div>

        <div className="app-body">
          {activeTab === 'dashboard' && activeDashboardRole === 'EV_OWNER' && (
            <EVOwnerDashboard
              user={user}
              chargers={mockChargers}
              onSelectCharger={handleSelectCharger}
              onExploreChargers={() => setActiveTab('find')}
              onBookNow={() => setActiveTab('find')}
              onOpenBookings={() => setActiveTab('bookings')}
              onOpenWallet={() => setActiveTab('wallet')}
            />
          )}
          {showHostWorkspace && <HostWorkspace tab={activeTab} token={authToken} hostName={user.name} onNavigate={setActiveTab} onCountsChange={setHostCounts} />}
          {showCompanyWorkspace && <CompanyWorkspace tab={activeTab} token={authToken} companyName={user.name} onNavigate={setActiveTab} onCountsChange={setCompanyCounts} />}

          {showOwnerBookings && <BookingsView bookings={bookings} />}

          {showOwnerWallet && <WalletView token={authToken} onOpenVehicles={() => setActiveTab('vehicles')} />}

          {activeTab === 'find' && activeDashboardRole === 'EV_OWNER' && (
            <div style={{ height: 'calc(100dvh - 180px)', minHeight: 520 }}>
              <MapView
                chargers={mockChargers}
                selectedId={selectedCharger?.id ?? null}
                onSelect={handleSelectCharger}
                filter={mapFilter}
                onFilterChange={setMapFilter}
              />
            </div>
          )}

          {showModeFeature && <FeatureView role={activeDashboardRole} tab={activeTab} />}
        </div>
      </main>

      <ChargerDetailModal
        charger={modalCharger}
        onClose={() => setModalCharger(null)}
        onConfirmBooking={handleConfirmBooking}
      />
    </div>
  );
}

export default App;
