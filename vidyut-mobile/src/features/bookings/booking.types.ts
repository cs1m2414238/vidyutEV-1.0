export interface CreateBookingRequest {
  stationId: number | string;
  vehicleId?: number;
  startTime: string;
  durationMinutes: number;
}

export interface BookingItem {
  id: string | number;
  stationId: string | number;
  vehicleId?: number;
  chargerName: string;
  address: string;
  startTime: string;
  durationMinutes: number;
  totalCost: number;
  status: 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  createdAt: string;
}
