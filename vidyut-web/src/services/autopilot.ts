import { apiRequest } from './api';

export type AutopilotTripStatus =
  | 'RESERVED'
  | 'MONITORING'
  | 'REROUTED'
  | 'PAYMENT_REQUIRED'
  | 'COMPLETED'
  | 'CANCELLED';

export type AutopilotStopStatus = 'PLANNED' | 'RESERVED' | 'CANCELLED' | 'COMPLETED';
export type AutopilotActionState = 'SUCCESS' | 'INFO' | 'WARNING';
export type AutopilotMode = 'RECOMMEND_ONLY' | 'ASK_BEFORE_ACTIONS' | 'FULL_AUTOPILOT';
export type TripPurpose = 'GENERAL' | 'MALL_VISIT' | 'REST_STOP' | 'COMMUTE' | 'DESTINATION_CHARGING';

export interface AutopilotVehicle {
  id: number;
  userId: number;
  makeAndModel: string;
  registrationNumber: string;
  batteryCapacity: string;
  connectorType: string;
}

export interface AutopilotTripRequest {
  vehicleId: number;
  origin: string;
  destination: string;
  goal: string;
  tripPurpose: TripPurpose;
  arrivalDeadline: string;
  optimizeFor: 'TIME' | 'COST' | 'BALANCED';
  autonomyMode: AutopilotMode;
  currentBatteryPercent: number;
  minimumArrivalBatteryPercent: number;
  maximumChargingBudget: number;
  idempotencyKey: string;
}

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
  selectionReason?: string;
  status: AutopilotStopStatus;
}

export interface AutopilotPlanStop {
  sequenceNumber: number;
  stationId: number;
  stationName: string;
  stationAddress: string;
  connectorType: string;
  powerKw: number;
  distanceFromOriginKm: number;
  estimatedArrivalTime: string;
  arrivalBatteryPercent: number;
  targetBatteryPercent: number;
  estimatedWaitMinutes: number;
  chargingMinutes: number;
  estimatedCost: number;
  availableConnectors: number;
  queueCount: number;
  rating: number;
  selectionReason?: string;
}

export interface AutopilotPlan {
  vehicleId: number;
  vehicleName: string;
  registrationNumber: string;
  connectorType: string;
  origin: string;
  destination: string;
  arrivalDeadline?: string;
  estimatedArrivalTime: string;
  optimizeFor: AutopilotTripRequest['optimizeFor'];
  tripPurpose: TripPurpose;
  purposeSummary: string;
  pastExperiencesUsed: number;
  memorySummary: string;
  autonomyMode: AutopilotMode;
  currentBatteryPercent: number;
  minimumArrivalBatteryPercent: number;
  maximumChargingBudget: number;
  totalDistanceKm: number;
  estimatedDriveMinutes: number;
  totalDurationMinutes: number;
  estimatedChargingCost: number;
  budgetRemaining: number;
  estimatedArrivalBatteryPercent: number;
  compatibleChargersEvaluated: number;
  withinBudget: boolean;
  safeArrivalReserve: boolean;
  liveAvailabilityChecked: boolean;
  confirmationRequired: boolean;
  stops: AutopilotPlanStop[];
}

export interface AutopilotAction {
  sequenceNumber: number;
  state: AutopilotActionState;
  title: string;
  detail: string;
  timestamp: string;
}

export interface AutopilotTrip {
  id: number;
  idempotencyKey: string;
  goal: string;
  tripPurpose: TripPurpose;
  memorySummary?: string;
  origin: string;
  destination: string;
  arrivalDeadline?: string;
  optimizeFor: 'TIME' | 'COST' | 'BALANCED';
  autonomyMode: AutopilotMode;
  minimumArrivalBatteryPercent: number;
  maximumChargingBudget: number;
  totalDistanceKm: number;
  estimatedDriveMinutes: number;
  totalDurationMinutes: number;
  estimatedChargingCost: number;
  estimatedArrivalBatteryPercent: number;
  activeStationId?: number;
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
  createdAt: string;
  updatedAt: string;
}

export interface AutopilotAgentResponse {
  sessionId: string;
  requestId: string;
  reply: string;
  model: string;
  toolCalls: Array<{ name: string; status: string }>;
  plan?: AutopilotPlan;
  actionResult?: AutopilotTrip;
}

function authorized(token: string): HeadersInit {
  return { Authorization: `Bearer ${token}` };
}

export function getAutopilotVehicles(token: string): Promise<AutopilotVehicle[]> {
  return apiRequest<AutopilotVehicle[]>('/ev/vehicles', {
    method: 'GET',
    headers: authorized(token),
  });
}

export function addAutopilotVehicle(
  token: string,
  vehicle: Omit<AutopilotVehicle, 'id' | 'userId'>,
): Promise<AutopilotVehicle> {
  return apiRequest<AutopilotVehicle>('/ev/vehicles', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify(vehicle),
  });
}

export function getCurrentAutopilotTrip(token: string): Promise<AutopilotTrip | null> {
  return apiRequest<AutopilotTrip | null>('/ev/autopilot/trips/current', {
    method: 'GET',
    headers: authorized(token),
  });
}

export function launchAutopilotTrip(token: string, request: AutopilotTripRequest): Promise<AutopilotTrip> {
  return apiRequest<AutopilotTrip>('/ev/autopilot/trips', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify(request),
  });
}

export function previewAutopilotTrip(token: string, request: AutopilotTripRequest): Promise<AutopilotPlan> {
  return apiRequest<AutopilotPlan>('/ev/autopilot/trips/preview', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify(request),
  });
}

export function startAutopilotTrip(token: string, tripId: number): Promise<AutopilotTrip> {
  return apiRequest<AutopilotTrip>(`/ev/autopilot/trips/${tripId}/start`, {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify({ batteryDropPercent: 6 }),
  });
}

export function simulateAutopilotFault(token: string, tripId: number): Promise<AutopilotTrip> {
  return apiRequest<AutopilotTrip>(`/ev/autopilot/trips/${tripId}/simulate-fault`, {
    method: 'POST',
    headers: authorized(token),
    body: '{}',
  });
}

export function completeAutopilotCharging(token: string, tripId: number): Promise<AutopilotTrip> {
  return apiRequest<AutopilotTrip>(`/ev/autopilot/trips/${tripId}/complete-charging`, {
    method: 'POST',
    headers: authorized(token),
    body: '{}',
  });
}

export function recordAutopilotExperience(
  token: string,
  tripId: number,
  payload: {
    stationId?: number;
    outcome: 'SUCCESS' | 'CHARGER_FAULT' | 'EXCESS_WAIT' | 'ACCESS_ISSUE' | 'PAYMENT_ISSUE' | 'USER_REPORTED';
    detail?: string;
    rating?: number;
    delayMinutes?: number;
  },
): Promise<unknown> {
  return apiRequest(`/ev/autopilot/trips/${tripId}/experience`, {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify(payload),
  });
}

export async function topUpAutopilotWallet(token: string, amount: number): Promise<void> {
  await apiRequest('/ev/wallet/topup', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify({ amount, paymentMethod: 'DEMO_UPI' }),
  });
}

export function sendAutopilotAgentMessage(
  token: string,
  message: string,
  sessionId?: string,
): Promise<AutopilotAgentResponse> {
  return apiRequest<AutopilotAgentResponse>('/ev/agent/chat', {
    method: 'POST',
    headers: authorized(token),
    body: JSON.stringify({
      message,
      sessionId,
      requestId: `web-agent-${Date.now()}`,
    }),
  });
}
