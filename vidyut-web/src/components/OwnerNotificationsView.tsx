import { Bell, RefreshCw } from 'lucide-react';
import type { EvNotification } from '../services/notifications';

interface OwnerNotificationsViewProps {
  notifications: EvNotification[];
  loading: boolean;
  error?: string;
  onRefresh: () => void;
}

function formatNotificationTime(timestamp: string): string {
  const value = new Date(timestamp);
  if (Number.isNaN(value.getTime())) return timestamp;
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(value);
}

export function OwnerNotificationsView({
  notifications,
  loading,
  error,
  onRefresh,
}: OwnerNotificationsViewProps) {
  const unreadCount = notifications.filter((notification) => !notification.read).length;

  return (
    <section className="owner-notifications-view" aria-labelledby="owner-notifications-title">
      <header className="owner-notifications-head">
        <div>
          <span className="section-eyebrow">ACCOUNT ACTIVITY</span>
          <h1 id="owner-notifications-title">Notifications</h1>
          <p>{unreadCount > 0 ? `${unreadCount} unread update${unreadCount === 1 ? '' : 's'}` : 'You are all caught up.'}</p>
        </div>
        <button type="button" onClick={onRefresh} disabled={loading}>
          <RefreshCw size={15} className={loading ? 'spinning' : ''} /> Refresh
        </button>
      </header>

      {error && <div className="owner-notifications-error" role="alert">{error}</div>}

      <div className="owner-notifications-card">
        {loading && notifications.length === 0 && (
          <div className="owner-notifications-empty">Loading notifications…</div>
        )}

        {!loading && notifications.length === 0 && !error && (
          <div className="owner-notifications-empty">
            <Bell size={25} />
            <strong>No notifications yet</strong>
            <p>Booking, charging and account updates will appear here.</p>
          </div>
        )}

        {notifications.map((notification) => (
          <article
            key={notification.id}
            className={`owner-notification-row ${notification.read ? '' : 'unread'}`}
          >
            <span className="owner-notification-icon"><Bell size={17} /></span>
            <div>
              <small>{notification.type.replaceAll('_', ' ')}</small>
              <strong>{notification.title}</strong>
              <p>{notification.message}</p>
            </div>
            <time dateTime={notification.timestamp}>{formatNotificationTime(notification.timestamp)}</time>
          </article>
        ))}
      </div>
    </section>
  );
}
