import { MapPin, Calendar, Wallet, AlertCircle, Info, Navigation, Sparkles, BatteryCharging, Bluetooth, Route, ChevronRight } from 'lucide-react';
import type { User, Charger } from '../../types';
import type { Vehicle } from '../../services/vehicles';

interface EVOwnerDashboardProps {
  user: User;
  chargers: Charger[];
  onSelectCharger?: (charger: Charger) => void;
  onExploreChargers?: () => void;
  onBookNow?: () => void;
  onOpenBookings?: () => void;
  onOpenWallet?: () => void;
  onOpenAutopilot?: () => void;
  vehicle?: Vehicle | null;
  onOpenVehicle?: (vehicleId: number) => void;
}

export function EVOwnerDashboard({
  user,
  chargers,
  onSelectCharger,
  onExploreChargers,
  onBookNow,
  onOpenBookings,
  onOpenWallet,
  onOpenAutopilot,
  vehicle,
  onOpenVehicle,
}: EVOwnerDashboardProps) {
  const selectedStation = chargers[0] || {
    id: 1,
    name: 'Green Park Station',
    hostName: 'Vidyut Hub',
    address: '1.2 km away • Green Park Extension',
    latitude: 28.5588,
    longitude: 77.2028,
    pricePerKwh: 18,
    connectorType: 'CCS2',
    powerKw: 150,
    available: true,
    rating: 4.9,
    reviewCount: 128,
    distance: '1.2 km',
    imageUrl: '',
  };

  const batteryPercent = vehicle?.batteryPercent == null ? null : Math.max(0, Math.min(100, vehicle.batteryPercent));
  const vehicleStatus = vehicle?.charging == null ? 'Status unknown' : vehicle.charging ? 'Charging' : 'Not charging';
  const vehicleStatusClass = vehicle?.charging == null ? 'unknown' : vehicle.charging ? 'charging' : 'idle';

  return (
    <div className="ev-dashboard-container">
      {/* Top Welcome Banner */}
      <div className="dashboard-header-row">
        <div className="welcome-text-group">
          <h1 className="welcome-heading">Good Morning, {user.name || 'Priyanshu'} 👋</h1>
          <p className="welcome-subheading">Where do you want to charge today?</p>
        </div>

      </div>

      <button type="button" className="ev-autopilot-banner" onClick={onOpenAutopilot}>
        <span className="ev-autopilot-banner-icon"><Navigation size={22} /></span>
        <span className="ev-autopilot-banner-copy">
          <small><Sparkles size={12} /> NEW · VIDYUT AUTOPILOT</small>
          <strong>Tell us where you’re going. We’ll plan, reserve, reroute and pay.</strong>
        </span>
        <span className="ev-autopilot-banner-action">Plan a journey →</span>
      </button>

      {/* Main Top 3 Cards Grid */}
      <div className="ev-top-grid">
        {/* Card 1: Interactive Map Card */}
        <div className="dashboard-card map-card-container">
          <div className="map-view-canvas">
            <div className="map-card-caption">
              <small>NEARBY NETWORK</small>
              <strong>Nearest available charger</strong>
            </div>
            {/* Soft Map SVG Graphic with Location Pins */}
            <svg width="100%" height="100%" viewBox="0 0 600 340" preserveAspectRatio="none" className="map-svg-bg">
              <defs>
                <pattern id="gridPattern" width="40" height="40" patternUnits="userSpaceOnUse">
                  <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#e2e8f0" strokeWidth="1" opacity="0.6" />
                </pattern>
              </defs>
              <rect width="100%" height="100%" fill="#f8fafc" />
              <rect width="100%" height="100%" fill="url(#gridPattern)" />
              {/* Roads / Paths */}
              <path d="M 0 100 Q 200 80 400 120 T 600 90" stroke="#cbd5e1" strokeWidth="12" fill="none" />
              <path d="M 120 0 L 140 340" stroke="#e2e8f0" strokeWidth="16" fill="none" />
              <path d="M 380 0 L 360 340" stroke="#e2e8f0" strokeWidth="14" fill="none" />
              <path d="M 0 240 Q 300 220 600 260" stroke="#cbd5e1" strokeWidth="10" fill="none" />

              {/* Station Pins */}
              <g className="map-pin-group">
                {/* Pin 1 */}
                <circle cx="160" cy="90" r="18" fill="#dcfce7" />
                <circle cx="160" cy="90" r="12" fill="#22c55e" />
                <path d="M 158 84 L 155 91 L 160 91 L 158 97 L 165 89 L 160 89 Z" fill="#ffffff" />

                {/* Pin 2 */}
                <circle cx="275" cy="70" r="18" fill="#dcfce7" />
                <circle cx="275" cy="70" r="12" fill="#22c55e" />
                <path d="M 273 64 L 270 71 L 275 71 L 273 77 L 280 69 L 275 69 Z" fill="#ffffff" />

                {/* Pin 3 - User Dot (Blue) */}
                <circle cx="270" cy="160" r="22" fill="rgba(59, 130, 246, 0.2)" />
                <circle cx="270" cy="160" r="12" fill="#3b82f6" stroke="#ffffff" strokeWidth="3" />

                {/* Pin 4 */}
                <circle cx="430" cy="120" r="18" fill="#dcfce7" />
                <circle cx="430" cy="120" r="12" fill="#22c55e" />
                <path d="M 428 114 L 425 121 L 430 121 L 428 127 L 435 119 L 430 119 Z" fill="#ffffff" />

                {/* Pin 5 */}
                <circle cx="160" cy="220" r="18" fill="#dcfce7" />
                <circle cx="160" cy="220" r="12" fill="#22c55e" />
                <path d="M 158 214 L 155 221 L 160 221 L 158 227 L 165 219 L 160 219 Z" fill="#ffffff" />

                {/* Pin 6 */}
                <circle cx="360" cy="230" r="18" fill="#dcfce7" />
                <circle cx="360" cy="230" r="12" fill="#22c55e" />
                <path d="M 358 224 L 355 231 L 360 231 L 358 237 L 365 229 L 360 229 Z" fill="#ffffff" />
              </g>
            </svg>

            {/* Selected Floating Station Card Overlay */}
            <div className="map-overlay-card">
              <div className="overlay-thumb">
                <div className="charger-icon-badge">⚡</div>
              </div>
              <div className="overlay-details">
                <div className="overlay-title-row">
                  <h4 className="overlay-title">{selectedStation.name}</h4>
                  <span className="available-tag">
                    <span className="dot-active" /> Available
                  </span>
                </div>
                <p className="overlay-sub">{selectedStation.distance || '1.2 km away'}</p>
                <p className="overlay-power">DC Fast • {selectedStation.powerKw || 150} kW</p>
                <button
                  type="button"
                  className="btn-overlay-details"
                  onClick={() => onSelectCharger?.(selectedStation)}
                >
                  View Details
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Card 2: Quick Actions */}
        <div className="dashboard-card quick-actions-card">
          <h3 className="card-heading">Quick Actions</h3>
          <div className="quick-actions-grid">
            <button
              type="button"
              className="action-tile green-tile"
              onClick={onExploreChargers}
            >
              <div className="action-icon-bg green-icon">
                <MapPin size={22} />
              </div>
              <span>Find Charger</span>
            </button>

            <button
              type="button"
              className="action-tile green-light-tile"
              onClick={onBookNow}
            >
              <div className="action-icon-bg green-soft-icon">
                <Calendar size={22} />
              </div>
              <span>Book Now</span>
            </button>

            <button type="button" className="action-tile blue-tile" onClick={onOpenBookings}>
              <div className="action-icon-bg blue-icon">
                <Calendar size={22} />
              </div>
              <span>My Bookings</span>
            </button>

            <button type="button" className="action-tile purple-tile" onClick={onOpenWallet}>
              <div className="action-icon-bg purple-icon">
                <Wallet size={22} />
              </div>
              <span>Wallet</span>
            </button>
          </div>
        </div>

        {/* Card 3: Persisted vehicle status */}
        <div className="dashboard-card current-session-card">
          <div className="session-header-row">
            <div>
              <h3 className="card-heading">Vehicle status</h3>
              <p className="session-station-name">{vehicle?.makeAndModel || 'No vehicle selected'}</p>
            </div>
            <span className={`charging-status-badge ${vehicleStatusClass}`}>
              <span className="pulse-green" /> {vehicleStatus}
            </span>
          </div>

          <div className="session-metrics-row">
            <div className="metric-box">
              <span className="metric-label"><BatteryCharging size={12} /> Battery</span>
              <span className="metric-val">{batteryPercent == null ? 'Not synced' : `${batteryPercent}%`}</span>
            </div>
            <div className="metric-box">
              <span className="metric-label"><Route size={12} /> Range</span>
              <span className="metric-val">{vehicle?.remainingRangeKm == null ? 'Not synced' : `${Math.round(vehicle.remainingRangeKm)} km`}</span>
            </div>
            <div className="metric-box">
              <span className="metric-label"><Bluetooth size={12} /> Connection</span>
              <span className="metric-val">{vehicle?.connectionStatus === 'CONNECTED' ? 'Connected' : vehicle?.connectionStatus === 'DISCONNECTED' ? 'Offline' : 'Not synced'}</span>
            </div>
          </div>

          <div className="battery-progress-section">
            <div className="battery-label-row">
              <span className="battery-percent">{batteryPercent == null ? 'Battery reading unavailable' : `${batteryPercent}% remaining`}</span>
              <span className="vehicle-status-source">{vehicle?.telemetrySource === 'BLUETOOTH_DEMO' ? 'Bluetooth demo' : vehicle?.telemetrySource === 'BLUETOOTH' ? 'Bluetooth' : vehicle?.telemetrySource === 'CHARGING_SESSION' ? 'Charging session' : vehicle?.telemetrySource === 'MANUAL' ? 'Manual' : 'No source'}</span>
            </div>
            <div className="battery-bar-track">
              <div className={`battery-bar-fill ${batteryPercent == null ? 'unknown' : ''}`} style={{ width: `${batteryPercent ?? 0}%` }} />
            </div>
          </div>

          <div className="dashboard-vehicle-summary">
            <p>{vehicle
              ? vehicle.charging
                ? 'The dashboard shows charging because the latest saved vehicle reading reports active charging.'
                : vehicle.charging === false
                  ? 'The latest saved vehicle reading reports that it is not charging.'
                  : 'Connect by Bluetooth or add a manual reading to determine its charging state.'
              : 'Add a vehicle to see battery, range and charging information here.'}</p>
            {vehicle && onOpenVehicle && (
              <button type="button" onClick={() => onOpenVehicle(vehicle.id)}>View vehicle <ChevronRight size={14} /></button>
            )}
          </div>
        </div>
      </div>

      {/* Bottom Section: Recent Bookings Table */}
      <div className="dashboard-card recent-bookings-card">
        <div className="bookings-table-header">
          <h3 className="card-heading">Recent Bookings</h3>
          <button type="button" className="btn-text-link" onClick={onOpenBookings}>View All</button>
        </div>

        <div className="table-responsive-wrapper">
          <table className="bookings-custom-table">
            <thead>
              <tr>
                <th>Bookings</th>
                <th>Time Elapsed</th>
                <th>Energy Delivered</th>
                <th>Amount</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <div className="booking-title-cell">
                    <div className="icon-warn-badge">
                      <AlertCircle size={16} />
                    </div>
                    <div>
                      <div className="cell-main-title">Cyber Hub Station</div>
                      <div className="cell-sub-info">21 May 2024 • 6:30 PM</div>
                    </div>
                  </div>
                </td>
                <td>
                  <span className="mono-time">00:32:45 - 6:30 PM</span>
                </td>
                <td>
                  <span>12.45 kWh • AC Charger</span>
                </td>
                <td>
                  <span className="font-semibold">Amount ₹285.60</span>
                </td>
                <td>
                  <span className="amount-highlight">₹186.40</span>
                </td>
              </tr>

              <tr>
                <td>
                  <div className="booking-title-cell">
                    <div className="icon-info-badge">
                      <Info size={16} />
                    </div>
                    <div>
                      <div className="cell-main-title">MG Road Station</div>
                      <div className="cell-sub-info">Maintenance scheduled • 21 May 2024 • 6:30 PM</div>
                    </div>
                  </div>
                </td>
                <td>
                  <span className="mono-time">21 May 2024 • 6:30 PM</span>
                </td>
                <td>
                  <span>1h 45m • AC Charger</span>
                </td>
                <td>
                  <span className="font-semibold">Amount ₹255.00</span>
                </td>
                <td>
                  <span className="status-pill-completed">Completed</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
