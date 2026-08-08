import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import { CreateVehicleRequest, VehicleItem } from './vehicle.types';

export async function getMyVehicles(): Promise<VehicleItem[]> {
  try {
    const response = await apiClient.get<ApiResponse<VehicleItem[]>>('/ev/vehicles');
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to load your vehicles.'));
  }
}

export async function addVehicle(request: CreateVehicleRequest): Promise<VehicleItem> {
  try {
    const response = await apiClient.post<ApiResponse<VehicleItem>>('/ev/vehicles', request);
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to add your vehicle.'));
  }
}
