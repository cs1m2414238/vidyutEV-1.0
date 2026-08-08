export interface VehicleItem {
  id: number;
  userId: number;
  makeAndModel: string;
  registrationNumber: string;
  batteryCapacity?: string;
  connectorType?: string;
}

export interface CreateVehicleRequest {
  makeAndModel: string;
  registrationNumber: string;
  batteryCapacity?: string;
  connectorType?: string;
}
