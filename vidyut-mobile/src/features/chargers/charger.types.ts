export interface Charger {
  id: number | string;
  name: string;
  hostName: string;
  address: string;
  latitude: number;
  longitude: number;
  pricePerKwh: number;
  connectorType: 'TYPE_2' | 'CCS2' | 'CHAdeMO' | string;
  powerKw: number;
  available: boolean;
  rating: number;
  reviewCount?: number;
  distance?: string;
  imageUrl?: string;
  description?: string;
}
