export type AccessMode = 'EV_USER' | 'HOST' | 'COMPANY' | 'ADMIN';

export interface ApiUser {
  id: number | string;
  email: string;
  fullName: string;
  phone?: string;
  role: 'ROLE_EV_USER' | 'ROLE_HOST' | 'ROLE_COMPANY' | 'ROLE_ADMIN';
  roles: string[];
  accountType: 'INDIVIDUAL' | 'COMPANY' | 'ADMIN';
  allowedModes: AccessMode[];
  defaultMode: AccessMode;
}

export interface AuthData {
  token: string;
  activeMode: AccessMode;
  user: ApiUser;
}

interface ApiEnvelope<T> {
  success: boolean;
  message: string;
  data: T;
}

interface ApiErrorBody {
  message?: string;
  details?: string[];
}

const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL?.trim() || '/api'
).replace(/\/+$/, '');

export async function apiRequest<T>(path: string, init: RequestInit): Promise<T> {
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...init.headers,
      },
    });
  } catch {
    throw new Error('Unable to reach the Vidyut server. Check that the backend is running.');
  }

  const contentType = response.headers.get('content-type') ?? '';
  const body = contentType.includes('application/json') ? await response.json() : await response.text();
  if (!response.ok) {
    const errorBody = typeof body === 'object' && body !== null ? body as ApiErrorBody : null;
    throw new Error(errorBody?.details?.[0] || errorBody?.message || String(body) || 'Request failed.');
  }

  const envelope = body as ApiEnvelope<T>;
  if (!envelope.success || envelope.data === undefined) {
    throw new Error(envelope.message || 'The server returned an invalid response.');
  }
  return envelope.data;
}

export async function apiDownload(path: string, token: string): Promise<Blob> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as ApiErrorBody | null;
    throw new Error(body?.details?.[0] || body?.message || 'Unable to download the report.');
  }
  return response.blob();
}

export async function switchAuthMode(mode: AccessMode, token: string): Promise<AuthData> {
  return apiRequest<AuthData>('/auth/switch-mode', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ mode }),
  });
}

export function saveAuthSession(auth: AuthData): void {
  localStorage.setItem('vidyut_token', auth.token);
  localStorage.setItem('vidyut_user', JSON.stringify(auth.user));
  localStorage.setItem('vidyut_active_mode', auth.activeMode);
}

export function loadAuthSession(): AuthData | null {
  try {
    const token = localStorage.getItem('vidyut_token');
    const userJson = localStorage.getItem('vidyut_user');
    const activeMode = localStorage.getItem('vidyut_active_mode') as AccessMode | null;

    if (!token || !userJson || !activeMode) return null;

    const user = JSON.parse(userJson) as ApiUser;
    if (!user.email || !Array.isArray(user.allowedModes) || !user.allowedModes.includes(activeMode)) {
      return null;
    }

    return { token, activeMode, user };
  } catch {
    return null;
  }
}

export function clearAuthSession(): void {
  localStorage.removeItem('vidyut_token');
  localStorage.removeItem('vidyut_user');
  localStorage.removeItem('vidyut_active_mode');
}
