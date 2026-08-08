import React from 'react';
import type { BookingItem } from '../types';

interface BookingsViewProps {
  bookings: BookingItem[];
}

export const BookingsView: React.FC<BookingsViewProps> = ({ bookings }) => {
  return (
    <div style={styles.container}>
      <h2 style={styles.title}>My Charging Sessions &amp; Bookings</h2>
      <div style={styles.tableCard}>
        <table style={styles.table}>
          <thead>
            <tr style={styles.thRow}>
              <th style={styles.th}>Booking ID</th>
              <th style={styles.th}>Station Name</th>
              <th style={styles.th}>Location</th>
              <th style={styles.th}>Date &amp; Time</th>
              <th style={styles.th}>Duration</th>
              <th style={styles.th}>Cost</th>
              <th style={styles.th}>Status</th>
            </tr>
          </thead>
          <tbody>
            {bookings.map((b) => (
              <tr key={b.id} style={styles.tr}>
                <td style={{ ...styles.td, fontWeight: 700 }}>{b.id}</td>
                <td style={styles.td}>{b.chargerName}</td>
                <td style={styles.td}>{b.address}</td>
                <td style={styles.td}>
                  {new Date(b.startTime).toLocaleString('en-IN', {
                    dateStyle: 'medium',
                    timeStyle: 'short',
                  })}
                </td>
                <td style={styles.td}>{b.durationMinutes} mins</td>
                <td style={{ ...styles.td, fontWeight: 700, color: '#00A86B' }}>
                  ₹{b.totalCost.toFixed(2)}
                </td>
                <td style={styles.td}>
                  <span
                    style={{
                      ...styles.chip,
                      backgroundColor:
                        b.status === 'COMPLETED'
                          ? '#DCFCE7'
                          : b.status === 'CONFIRMED'
                          ? '#E0F2FE'
                          : '#FEF3C7',
                      color:
                        b.status === 'COMPLETED'
                          ? '#15803D'
                          : b.status === 'CONFIRMED'
                          ? '#0369A1'
                          : '#D97706',
                    }}
                  >
                    {b.status}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    padding: 20,
  },
  title: {
    fontSize: 20,
    fontWeight: 800,
    color: '#1E293B',
    marginBottom: 16,
  },
  tableCard: {
    backgroundColor: '#fff',
    borderRadius: 16,
    border: '1px solid #E2E8F0',
    overflowX: 'auto',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
  },
  table: {
    width: '100%',
    minWidth: 820,
    borderCollapse: 'collapse',
    textAlign: 'left',
    fontSize: 13,
  },
  thRow: {
    backgroundColor: '#F8FAFC',
    borderBottom: '1px solid #E2E8F0',
  },
  th: {
    padding: '12px 16px',
    fontWeight: 700,
    color: '#64748B',
    fontSize: 11,
    textTransform: 'uppercase',
  },
  tr: {
    borderBottom: '1px solid #F1F5F9',
  },
  td: {
    padding: '14px 16px',
    color: '#1E293B',
  },
  chip: {
    fontSize: 10,
    fontWeight: 800,
    padding: '3px 8px',
    borderRadius: 8,
  },
};
