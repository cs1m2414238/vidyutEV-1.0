import { useState } from 'react';
import { Search, MapPin, Calendar, Wallet, AlertCircle, Info } from 'lucide-react';
import type { User, Charger } from '../../types';

interface EVOwnerDashboardProps {
  user: User;
  chargers: Charger[];
  onSelectCharger?: (charger: Charger) => void;
  onExploreChargers?: () => void;
  onBookNow?: () => void;
  onOpenBookings?: () => void;
  onOpenWallet?: () => void;
}

export function EVOwnerDashboard({
  user,
  chargers,
  onSelectCharger,
  onExploreChargers,
  onBookNow,
  onOpenBookings,
  onOpenWallet,
}: EVOwnerDashboardProps) {
  const [searchQuery, setSearchQuery] = useState('');
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

  const [isCharging, setIsCharging] = useState(true);

  return (
    <div className="ev-dashboard-container">
      {/* Top Welcome Banner & Search Header */}
      <div className="dashboard-header-row">
        <div className="welcome-text-group">
          <h1 className="welcome-heading">Good Morning, {user.name || 'Priyanshu'} 👋</h1>
          <p className="welcome-subheading">Where do you want to charge today?</p>
        </div>

        <div className="header-search-bar">
          <div className="search-input-wrapper">
            <Search size={18} className="search-icon-left" />
            <input
              type="text"
              placeholder="Search location or charger"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="search-input-field"
            />
            <button type="button" className="search-btn-accent" title="Search" onClick={onExploreChargers}>
              <Search size={18} />
            </button>
          </div>
        </div>
      </div>

      {/* Main Top 3 Cards Grid */}
      <div className="ev-top-grid">
        {/* Card 1: Interactive Map Card */}
        <div className="dashboard-card map-card-container">
          <div className="map-view-canvas">
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

        {/* Card 3: Current Session */}
        <div className="dashboard-card current-session-card">
          <div className="session-header-row">
            <div>
              <h3 className="card-heading">Current Session</h3>
              <p className="session-station-name">Green Park Station</p>
            </div>
            {isCharging && (
              <span className="charging-status-badge">
                <span className="pulse-green" /> Charging
              </span>
            )}
          </div>

          <div className="session-metrics-row">
            <div className="metric-box">
              <span className="metric-label">Time Elapsed</span>
              <span className="metric-val">00:32:45</span>
            </div>
            <div className="metric-box">
              <span className="metric-label">Energy Delivered</span>
              <span className="metric-val">12.45 kWh</span>
            </div>
            <div className="metric-box">
              <span className="metric-label">Amount</span>
              <span className="metric-val">₹285.60</span>
            </div>
          </div>

          <div className="battery-progress-section">
            <div className="battery-label-row">
              <span className="battery-percent">⚡ 68%</span>
              <button
                type="button"
                className="stop-session-btn"
                onClick={() => setIsCharging(false)}
              >
                {isCharging ? 'Stop' : 'Ended'}
              </button>
            </div>
            <div className="battery-bar-track">
              <div className="battery-bar-fill" style={{ width: '68%' }} />
            </div>
          </div>

          {/* Mini Sparkline Chart Preview */}
          <div className="session-sparkline-wrap">
            <svg width="100%" height="70" viewBox="0 0 300 70" preserveAspectRatio="none">
              <defs>
                <linearGradient id="sessionGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#22c55e" stopOpacity="0.3" />
                  <stop offset="100%" stopColor="#22c55e" stopOpacity="0.0" />
                </linearGradient>
              </defs>
              <path
                d="M 0 50 Q 30 45 60 25 T 120 15 T 180 35 T 240 10 T 300 20 L 300 70 L 0 70 Z"
                fill="url(#sessionGrad)"
              />
              <path
                d="M 0 50 Q 30 45 60 25 T 120 15 T 180 35 T 240 10 T 300 20"
                fill="none"
                stroke="#22c55e"
                strokeWidth="2.5"
              />
              <circle cx="280" cy="16" r="4" fill="#22c55e" stroke="#ffffff" strokeWidth="2" />
            </svg>
            <div className="chart-tooltip-badge">₹1,250</div>
            <div className="chart-date-labels">
              <span>1 May</span>
              <span>8 May</span>
              <span>15 May</span>
              <span>22 May</span>
              <span>29 May</span>
            </div>
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
