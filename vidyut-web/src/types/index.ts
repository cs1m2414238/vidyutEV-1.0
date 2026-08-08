export interface Charger {
  id: number;
  name: string;
  hostName: string;
  address: string;
  latitude: number;
  longitude: number;
  pricePerKwh: number;
  connectorType: string;
  powerKw: number;
  available: boolean;
  rating: number;
  reviewCount: number;
  distance: string;
  imageUrl: string;
  description?: string;
}

export interface BookingItem {
  id: string;
  chargerId: number;
  chargerName: string;
  address: string;
  startTime: string;
  durationMinutes: number;
  totalCost: number;
  status: 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  energyDelivered?: number;
}

export interface User {
  id: string;
  name: string;
  email: string;
  phone: string;
  walletBalance: number;
  totalBookings: number;
  totalEnergyKwh: number;
  co2SavedKg: number;
}

export type NavItem = {
  icon: string;
  label: string;
  id: string;
  badge?: number;
};
