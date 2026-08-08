import { useState } from 'react';
import { Bell, ChevronDown, MapPin, Menu, Search } from 'lucide-react';

interface TopBarProps {
  notificationCount?: number;
  onOpenMenu?: () => void;
}

export function TopBar({ notificationCount = 3, onOpenMenu }: TopBarProps) {
  const [searchValue, setSearchValue] = useState('');

  return (
    <header className="topbar">
      <button className="icon-button topbar-menu" onClick={onOpenMenu} aria-label="Open navigation">
        <Menu size={20} />
      </button>

      <label className="topbar-search">
        <Search size={17} color="#98a2b3" />
        <input
          aria-label="Search Vidyut"
          placeholder="Search location, charger or booking"
          value={searchValue}
          onChange={(event) => setSearchValue(event.target.value)}
        />
      </label>

      <div className="topbar-spacer" />

      <button className="location-button" type="button">
        <MapPin size={16} color="#0f8f5d" />
        Lucknow, India
        <ChevronDown size={14} color="#98a2b3" />
      </button>

      <button className="icon-button notification-button" type="button" aria-label={`${notificationCount} notifications`}>
        <Bell size={19} />
        {notificationCount > 0 && <span className="notification-dot" />}
      </button>
    </header>
  );
}
