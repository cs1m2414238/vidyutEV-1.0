import React, { useState } from 'react';
import type { Charger } from '../types';

interface ChargerDetailModalProps {
  charger: Charger | null;
  onClose: () => void;
  onConfirmBooking: (charger: Charger, duration: number) => void;
}

export const ChargerDetailModal: React.FC<ChargerDetailModalProps> = ({
  charger,
  onClose,
  onConfirmBooking,
}) => {
  const [duration, setDuration] = useState(60);
  const [isBooking, setIsBooking] = useState(false);

  if (!charger) return null;

  const totalCost = (charger.pricePerKwh * (charger.powerKw * (duration / 60))).toFixed(2);

  const handleBook = () => {
    setIsBooking(true);
    setTimeout(() => {
      onConfirmBooking(charger, duration);
      setIsBooking(false);
      onClose();
    }, 600);
  };

  return (
    <div style={styles.overlay} onClick={onClose}>
      <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
        <button style={styles.closeBtn} onClick={onClose}>✕</button>

        <img src={charger.imageUrl} alt={charger.name} style={styles.image} />

        <div style={styles.header}>
          <div>
            <h2 style={styles.title}>{charger.name}</h2>
            <div style={styles.host}>Hosted by {charger.hostName} • {charger.address}</div>
          </div>
          <span
            style={{
              ...styles.badge,
              backgroundColor: charger.available ? '#DCFCE7' : '#FFEDD5',
              color: charger.available ? '#15803D' : '#C2410C',
            }}
          >
            {charger.available ? 'Available' : 'Busy'}
          </span>
        </div>

        <div style={styles.specsGrid}>
          <div style={styles.specBox}>
            <div style={styles.specLabel}>Power</div>
            <div style={styles.specVal}>{charger.powerKw} kW</div>
          </div>
          <div style={styles.specBox}>
            <div style={styles.specLabel}>Connector</div>
            <div style={styles.specVal}>{charger.connectorType}</div>
          </div>
          <div style={styles.specBox}>
            <div style={styles.specLabel}>Rate</div>
            <div style={styles.specVal}>₹{charger.pricePerKwh}/kWh</div>
          </div>
          <div style={styles.specBox}>
            <div style={styles.specLabel}>Rating</div>
            <div style={styles.specVal}>⭐ {charger.rating}</div>
          </div>
        </div>

        {/* Duration selector */}
        <div style={styles.section}>
          <div style={styles.sectionTitle}>Select Charging Duration</div>
          <div style={styles.durationRow}>
            {[30, 45, 60, 90, 120].map((mins) => (
              <button
                key={mins}
                style={{
                  ...styles.durationBtn,
                  ...(duration === mins ? styles.durationBtnActive : {}),
                }}
                onClick={() => setDuration(mins)}
              >
                {mins} mins
              </button>
            ))}
          </div>
        </div>

        {/* Cost breakdown */}
        <div style={styles.costBox}>
          <div style={styles.costRow}>
            <span>Estimated Cost ({duration} mins):</span>
            <span style={styles.costVal}>₹{totalCost}</span>
          </div>
        </div>

        <button
          style={{
            ...styles.bookBtn,
            opacity: charger.available && !isBooking ? 1 : 0.6,
          }}
          disabled={!charger.available || isBooking}
          onClick={handleBook}
        >
          {isBooking ? 'Confirming Booking...' : charger.available ? 'Confirm & Book Now' : 'Currently Unavailable'}
        </button>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  overlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
    backdropFilter: 'blur(4px)',
  },
  modal: {
    backgroundColor: '#fff',
    borderRadius: 20,
    width: '90%',
    maxWidth: 520,
    padding: 24,
    position: 'relative',
    boxShadow: '0 20px 40px rgba(0,0,0,0.2)',
  },
  closeBtn: {
    position: 'absolute',
    top: 16,
    right: 16,
    fontSize: 18,
    color: '#64748B',
    cursor: 'pointer',
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#F1F5F9',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  image: {
    width: '100%',
    height: 180,
    borderRadius: 14,
    objectFit: 'cover',
    marginBottom: 16,
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  title: {
    fontSize: 18,
    fontWeight: 800,
    color: '#1E293B',
  },
  host: {
    fontSize: 12,
    color: '#64748B',
    marginTop: 2,
  },
  badge: {
    fontSize: 11,
    fontWeight: 700,
    padding: '4px 10px',
    borderRadius: 10,
  },
  specsGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(4, 1fr)',
    gap: 8,
    marginBottom: 16,
  },
  specBox: {
    backgroundColor: '#F8FAFC',
    borderRadius: 10,
    padding: 10,
    textAlign: 'center',
    border: '1px solid #F1F5F9',
  },
  specLabel: {
    fontSize: 10,
    color: '#64748B',
  },
  specVal: {
    fontSize: 13,
    fontWeight: 800,
    color: '#1E293B',
    marginTop: 2,
  },
  section: {
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: 700,
    color: '#1E293B',
    marginBottom: 8,
  },
  durationRow: {
    display: 'flex',
    gap: 8,
  },
  durationBtn: {
    flex: 1,
    padding: '8px',
    borderRadius: 8,
    border: '1px solid #E2E8F0',
    backgroundColor: '#fff',
    fontSize: 12,
    fontWeight: 600,
    color: '#64748B',
    cursor: 'pointer',
  },
  durationBtnActive: {
    backgroundColor: '#00A86B',
    borderColor: '#00A86B',
    color: '#fff',
  },
  costBox: {
    backgroundColor: '#E6F7F0',
    borderRadius: 10,
    padding: 12,
    marginBottom: 16,
  },
  costRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    fontSize: 13,
    fontWeight: 600,
    color: '#1E293B',
  },
  costVal: {
    fontSize: 18,
    fontWeight: 800,
    color: '#00A86B',
  },
  bookBtn: {
    width: '100%',
    padding: 14,
    borderRadius: 12,
    backgroundColor: '#00A86B',
    color: '#fff',
    fontSize: 14,
    fontWeight: 800,
    border: 'none',
    cursor: 'pointer',
  },
};
