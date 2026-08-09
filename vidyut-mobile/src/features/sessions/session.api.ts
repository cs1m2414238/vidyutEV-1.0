import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import { ChargingSession } from './session.types';

async function post(path: string, fallback: string): Promise<ChargingSession> {
  try { const response = await apiClient.post<ApiResponse<ChargingSession>>(path); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, fallback)); }
}
export const startSession = (bookingId: number | string) => post(`/ev/sessions/booking/${bookingId}/start`, 'Unable to start charging.');
export const stopSession = (id: number | string) => post(`/ev/sessions/${id}/stop`, 'Unable to stop charging.');
export const paySession = (id: number | string) => post(`/ev/sessions/${id}/pay`, 'Unable to pay for this session.');
export async function getSession(id: number | string): Promise<ChargingSession> {
  try { const response = await apiClient.get<ApiResponse<ChargingSession>>(`/ev/sessions/${id}`); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load the charging session.')); }
}
export async function getActiveSessions(): Promise<ChargingSession[]> {
  try { const response = await apiClient.get<ApiResponse<ChargingSession[]>>('/ev/sessions/active'); return unwrapApiResponse(response.data); }
  catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load active charging.')); }
}
