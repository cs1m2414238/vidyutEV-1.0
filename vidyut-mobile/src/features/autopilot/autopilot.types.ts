export type AutopilotTripStatus =
  | 'RESERVED'
  | 'MONITORING'
  | 'REROUTED'
  | 'PAYMENT_REQUIRED'
  | 'COMPLETED'
  | 'CANCELLED';

export interface AutopilotStop {
  id: number;
  sequenceNumber: number;
  stationId: number;
  bookingId?: number;
  stationName: string;
  stationAddress: string;
  connectorType: string;
  powerKw: number;
  distanceFromOriginKm: number;
  arrivalBatteryPercent: number;
  targetBatteryPercent: number;
  estimatedWaitMinutes: number;
  chargingMinutes: number;
  estimatedCost: number;
  status: 'PLANNED' | 'RESERVED' | 'CANCELLED' | 'COMPLETED';
}

export interface AutopilotAction {
  sequenceNumber: number;
  state: 'SUCCESS' | 'INFO' | 'WARNING';
  title: string;
  detail: string;
  timestamp: string;
}

export interface AutopilotTrip {
  id: number;
  goal: string;
  origin: string;
  destination: string;
  arrivalDeadline?: string;
  optimizeFor: 'TIME' | 'COST' | 'BALANCED';
  minimumArrivalBatteryPercent: number;
  maximumChargingBudget: number;
  totalDistanceKm: number;
  estimatedDriveMinutes: number;
  totalDurationMinutes: number;
  estimatedChargingCost: number;
  estimatedArrivalBatteryPercent: number;
  activeBookingId?: number;
  status: AutopilotTripStatus;
  paymentMessage?: string;
  walletBalance: number;
  telemetry: {
    vehicleId: number;
    vehicleName: string;
    registrationNumber: string;
    connectorType: string;
    batteryCapacityKwh: number;
    batteryPercent: number;
    remainingRangeKm: number;
    state: string;
  };
  stops: AutopilotStop[];
  timeline: AutopilotAction[];
}

export interface LaunchAutopilotTripRequest {
  vehicleId: number;
  origin: string;
  destination: string;
  goal: string;
  arrivalDeadline: string;
  optimizeFor: 'TIME' | 'COST' | 'BALANCED';
  currentBatteryPercent: number;
  minimumArrivalBatteryPercent: number;
  maximumChargingBudget: number;
  idempotencyKey: string;
}
