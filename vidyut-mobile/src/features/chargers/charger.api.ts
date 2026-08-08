import { apiClient } from '../../services/apiClient';
import { Charger } from './charger.types';
import { mockChargers } from './charger.mock';
import { CONFIG } from '../../constants/config';
import { ApiResponse, getApiErrorMessage, isNetworkError, unwrapApiResponse } from '../../services/apiResponse';

interface BackendConnector {
  type: string;
  powerKw: number;
  available: boolean;
}

interface BackendStation {
  id: number;
  name: string;
  address: string;
  city?: string;
  latitude: number;
  longitude: number;
  pricePerKwh: number;
  rating: number;
  reviewCount: number;
  imageUrl?: string;
  status: 'ACTIVE' | 'MAINTENANCE' | 'OFFLINE';
  availability: 'AVAILABLE' | 'CHARGING' | 'RESERVED' | 'UNAVAILABLE';
  hostUserId?: number;
  connectors?: BackendConnector[];
}

function normalizeStation(station: BackendStation): Charger {
  const connector = [...(station.connectors || [])]
    .sort((left, right) => right.powerKw - left.powerKw)[0];

  return {
    id: station.id,
    name: station.name,
    hostName: station.hostUserId ? `Vidyut Host #${station.hostUserId}` : station.name,
    address: [station.address, station.city].filter(Boolean).join(', '),
    latitude: station.latitude,
    longitude: station.longitude,
    pricePerKwh: station.pricePerKwh,
    connectorType: connector?.type || 'TYPE2',
    powerKw: connector?.powerKw || 0,
    available: station.status === 'ACTIVE'
      && station.availability === 'AVAILABLE'
      && (connector?.available ?? true),
    rating: station.rating,
    reviewCount: station.reviewCount,
    imageUrl: station.imageUrl,
  };
}

export async function getChargers(): Promise<Charger[]> {
  try {
    const response = await apiClient.get<ApiResponse<BackendStation[]>>('/stations');
    return unwrapApiResponse(response.data).map(normalizeStation);
  } catch (error) {
    if (CONFIG.USE_MOCK_DATA && isNetworkError(error)) return mockChargers;
    throw new Error(getApiErrorMessage(error, 'Unable to load charging stations.'));
  }
}

export async function getChargerById(id: number | string): Promise<Charger> {
  try {
    const response = await apiClient.get<ApiResponse<BackendStation>>(`/stations/${id}`);
    return normalizeStation(unwrapApiResponse(response.data));
  } catch (error) {
    if (CONFIG.USE_MOCK_DATA && isNetworkError(error)) {
      const found = mockChargers.find((charger) => charger.id.toString() === id.toString());
      if (found) return found;
    }
    throw new Error(getApiErrorMessage(error, 'Unable to load the charging station.'));
  }
}
