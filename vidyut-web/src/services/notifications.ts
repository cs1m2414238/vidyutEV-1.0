import { apiRequest } from './api';

interface NotificationPayload {
  id: number;
  userId: number;
  title: string;
  message: string;
  type: string;
  read?: boolean;
  isRead?: boolean;
  timestamp: string;
}

export interface EvNotification {
  id: number;
  title: string;
  message: string;
  type: string;
  read: boolean;
  timestamp: string;
}

export async function getEvNotifications(token: string): Promise<EvNotification[]> {
  const notifications = await apiRequest<NotificationPayload[]>('/ev/notifications', {
    method: 'GET',
    headers: { Authorization: `Bearer ${token}` },
  });

  return notifications.map((notification) => ({
    id: notification.id,
    title: notification.title,
    message: notification.message,
    type: notification.type,
    read: Boolean(notification.read ?? notification.isRead),
    timestamp: notification.timestamp,
  }));
}
