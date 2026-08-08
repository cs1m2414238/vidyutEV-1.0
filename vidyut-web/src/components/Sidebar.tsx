import type { LucideIcon } from 'lucide-react';
import {
  BatteryCharging,
  Building2,
  CalendarDays,
  Clock3,
  CarFront,
  CircleDollarSign,
  FileBarChart,
  Gauge,
  Bot,
  Bell,
  BadgeIndianRupee,
  Gift,
  Headphones,
  History,
  HousePlug,
  HeartPulse,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  Settings,
  ShieldCheck,
  Users,
  WalletCards,
  Wrench,
  X,
} from 'lucide-react';
import type { User } from '../types';

type UserRole = 'EV_OWNER' | 'LANDOWNER' | 'COMPANY_ADMIN';

interface SidebarItem {
  icon: LucideIcon;
  label: string;
  id: string;
}

interface SidebarProps {
  active: string;
  onNav: (id: string) => void;
  user: Pick<User, 'name' | 'email'>;
  bookingCount: number;
  onLogout: () => void;
  role?: UserRole;
  open?: boolean;
  onClose?: () => void;
}

const roleItems: Record<UserRole, SidebarItem[]> = {
  EV_OWNER: [
    { icon: LayoutDashboard, label: 'Dashboard', id: 'dashboard' },
    { icon: MapPinned, label: 'Find Charger', id: 'find' },
    { icon: CalendarDays, label: 'My Bookings', id: 'bookings' },
    { icon: History, label: 'Charging History', id: 'history' },
    { icon: WalletCards, label: 'Wallet', id: 'wallet' },
    { icon: CarFront, label: 'My Vehicles', id: 'vehicles' },
    { icon: HousePlug, label: 'Become a Host', id: 'host' },
    { icon: Gift, label: 'Rewards', id: 'rewards' },
    { icon: Headphones, label: 'Support', id: 'support' },
    { icon: Settings, label: 'Settings', id: 'settings' },
  ],
  LANDOWNER: [
    { icon: LayoutDashboard, label: 'Dashboard', id: 'dashboard' },
    { icon: HousePlug, label: 'My Chargers', id: 'chargers' },
    { icon: Clock3, label: 'Availability', id: 'availability' },
    { icon: CalendarDays, label: 'Bookings', id: 'bookings' },
    { icon: CircleDollarSign, label: 'Earnings', id: 'earnings' },
    { icon: HeartPulse, label: 'Monitoring', id: 'monitoring' },
    { icon: MessageSquare, label: 'Reviews', id: 'reviews' },
    { icon: Bot, label: 'AI Assistant', id: 'ai' },
    { icon: FileBarChart, label: 'Reports', id: 'reports' },
    { icon: Bell, label: 'Notifications', id: 'notifications' },
    { icon: Settings, label: 'Host Profile', id: 'profile' },
  ],
  COMPANY_ADMIN: [
    { icon: LayoutDashboard, label: 'Dashboard', id: 'dashboard' },
    { icon: Building2, label: 'Stations', id: 'stations' },
    { icon: BatteryCharging, label: 'Chargers', id: 'chargers' },
    { icon: CalendarDays, label: 'Bookings', id: 'bookings' },
    { icon: BadgeIndianRupee, label: 'Pricing', id: 'pricing' },
    { icon: Gauge, label: 'Analytics', id: 'analytics' },
    { icon: CircleDollarSign, label: 'Revenue', id: 'revenue' },
    { icon: Wrench, label: 'Maintenance', id: 'maintenance' },
    { icon: Users, label: 'Employees', id: 'users' },
    { icon: Bot, label: 'AI Assistant', id: 'ai' },
    { icon: FileBarChart, label: 'Reports', id: 'reports' },
    { icon: Bell, label: 'Notifications', id: 'notifications' },
    { icon: Settings, label: 'Settings', id: 'settings' },
  ],
};

const modeSummary: Record<UserRole, string> = {
  EV_OWNER: 'Personal charging, bookings and vehicle activity',
  LANDOWNER: 'Charger availability, earnings and guest bookings',
  COMPANY_ADMIN: 'Network health, operations and business reporting',
};

export function Sidebar({
  active,
  onNav,
  user,
  bookingCount,
  onLogout,
  role = 'EV_OWNER',
  open = false,
  onClose,
}: SidebarProps) {
  const initials = user.name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');

  const navigate = (id: string) => {
    onNav(id);
    onClose?.();
  };

  return (
    <>
      {open && <button className="drawer-scrim" aria-label="Close navigation" onClick={onClose} />}
      <aside className={`sidebar ${open ? 'open' : ''}`} aria-label="Primary navigation">
        <div className="sidebar-brand">
          <span className="brand-mark">
            <svg
              width="44"
              height="44"
              viewBox="0 0 200 200"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              style={{ filter: 'drop-shadow(0 4px 14px rgba(34, 197, 94, 0.65))' }}
            >
              <defs>
                <linearGradient id="vidyutSidebarGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#d9f99d" />
                  <stop offset="25%" stopColor="#a3e635" />
                  <stop offset="65%" stopColor="#22c55e" />
                  <stop offset="100%" stopColor="#15803d" />
                </linearGradient>
                <filter id="sidebarGlow" x="-20%" y="-20%" width="140%" height="140%">
                  <feGaussianBlur stdDeviation="6" result="blur" />
                  <feComposite in="SourceGraphic" in2="blur" operator="over" />
                </filter>
              </defs>
              <path
                d="M 38,30 L 65,36 L 100,165 L 128,75 L 114,80 L 165,15 L 142,58 L 158,58 L 100,185 Z"
                fill="url(#vidyutSidebarGrad)"
                filter="url(#sidebarGlow)"
              />
            </svg>
          </span>
          <div>
            <div className="brand-name">VIDYUT</div>
            <div className="brand-tagline">Powering a smarter tomorrow</div>
          </div>
          <button className="icon-button sidebar-close" onClick={onClose} aria-label="Close navigation">
            <X size={18} />
          </button>
        </div>

        <nav className="sidebar-nav">
          {roleItems[role].map(({ icon: Icon, label, id }) => (
            <button
              key={id}
              className={`sidebar-item ${active === id ? 'active' : ''}`}
              onClick={() => navigate(id)}
              aria-current={active === id ? 'page' : undefined}
            >
              <Icon size={18} strokeWidth={1.9} />
              <span className="sidebar-item-label">{label}</span>
              {id === 'bookings' && bookingCount > 0 && (
                <span className="sidebar-badge">{bookingCount}</span>
              )}
            </button>
          ))}
        </nav>

        <div className="sidebar-context">
          <div className="sidebar-context-top">
            <span className="sidebar-context-dot" />
            <ShieldCheck size={14} />
            Mode protected
          </div>
          <p>{modeSummary[role]}</p>
        </div>

        <div className="sidebar-user">
          <div className="sidebar-avatar">{initials || 'V'}</div>
          <div className="sidebar-user-copy">
            <div className="sidebar-user-name">{user.name}</div>
            <div className="sidebar-user-email">{user.email}</div>
          </div>
          <button className="icon-button" title="Log out" aria-label="Log out" onClick={onLogout}>
            <LogOut size={17} />
          </button>
        </div>
      </aside>
    </>
  );
}
