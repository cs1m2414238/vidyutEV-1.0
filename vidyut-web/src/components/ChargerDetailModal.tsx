import { useEffect, useMemo, useState } from 'react';
import { CalendarClock, CircleAlert, Gauge, MapPin, PlugZap, Star, X, Zap } from 'lucide-react';
import type { Charger } from '../types';
import './ChargerDetailModal.css';

interface ChargerDetailModalProps {
  charger: Charger | null;
  onClose: () => void;
  onConfirmBooking: (charger: Charger, durationMinutes: number) => Promise<void>;
}

export function ChargerDetailModal({ charger, onClose, onConfirmBooking }: ChargerDetailModalProps) {
  const [duration, setDuration] = useState(60);
  const [isBooking, setIsBooking] = useState(false);
  const [bookingError, setBookingError] = useState('');

  const durations = useMemo(() => {
    const slot = charger?.bookingSlotMinutes || 30;
    return [slot, slot * 2, slot * 3, slot * 4].filter((minutes) => minutes <= 720);
  }, [charger?.bookingSlotMinutes]);

  useEffect(() => {
    if (!charger) return;
    const preferredDuration = charger.bookingSlotMinutes && charger.bookingSlotMinutes >= 30
      ? charger.bookingSlotMinutes
      : 60;
    setDuration(preferredDuration);
    setBookingError('');
    setIsBooking(false);
  }, [charger]);

  useEffect(() => {
    if (!charger || isBooking) return undefined;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [charger, isBooking, onClose]);

  if (!charger) return null;

  const energy = charger.powerKw * (duration / 60);
  const totalCost = charger.pricePerKwh * energy;

  const handleBook = async () => {
    setIsBooking(true);
    setBookingError('');
    try {
      await onConfirmBooking(charger, duration);
      onClose();
    } catch (error) {
      setBookingError(error instanceof Error ? error.message : 'Unable to confirm this booking.');
    } finally {
      setIsBooking(false);
    }
  };

  return (
    <div className="charger-dialog-backdrop" role="presentation" onMouseDown={() => !isBooking && onClose()}>
      <section className="charger-dialog" role="dialog" aria-modal="true" aria-labelledby="charger-dialog-title" onMouseDown={(event) => event.stopPropagation()}>
        <button className="charger-dialog-close" type="button" onClick={onClose} disabled={isBooking} aria-label="Close charger details"><X size={18} /></button>
        <div className="charger-dialog-image-wrap">
          <img src={charger.imageUrl} alt="" className="charger-dialog-image" />
          <span className={charger.available ? 'available' : 'busy'}>{charger.available ? 'Available now' : 'Currently busy'}</span>
        </div>

        <div className="charger-dialog-body">
          <div className="charger-dialog-heading">
            <div>
              <span className="charger-dialog-eyebrow">Verified charging station</span>
              <h2 id="charger-dialog-title">{charger.name}</h2>
              <p><MapPin size={13} />{charger.address}</p>
            </div>
            <span className="charger-rating"><Star size={14} fill="currentColor" />{charger.rating}</span>
          </div>

          <div className="charger-spec-grid">
            <article><span><Gauge size={16} /></span><small>Power</small><strong>{charger.powerKw} kW</strong></article>
            <article><span><PlugZap size={16} /></span><small>Connector</small><strong>{charger.connectorType}</strong></article>
            <article><span><Zap size={16} /></span><small>Rate</small><strong>₹{charger.pricePerKwh}/kWh</strong></article>
          </div>

          <div className="charger-duration-section">
            <div><h3>Select charging duration</h3><p>Starting now • saved to My bookings</p></div>
            <div className="charger-duration-options">
              {durations.map((minutes) => (
                <button key={minutes} type="button" className={duration === minutes ? 'active' : ''} onClick={() => setDuration(minutes)} disabled={isBooking}>
                  {minutes < 60 ? `${minutes} min` : minutes % 60 === 0 ? `${minutes / 60} hr` : `${Math.floor(minutes / 60)}h ${minutes % 60}m`}
                </button>
              ))}
            </div>
          </div>

          <div className="charger-cost-summary">
            <span><CalendarClock size={18} /><div><small>Estimated session</small><strong>{energy.toFixed(1)} kWh • {duration} minutes</strong></div></span>
            <div><small>Estimated total</small><strong>₹{totalCost.toFixed(2)}</strong></div>
          </div>

          {bookingError && <div className="charger-booking-error" role="alert"><CircleAlert size={16} />{bookingError}</div>}

          <button className="charger-book-button" type="button" disabled={!charger.available || isBooking} onClick={() => void handleBook()}>
            {isBooking ? 'Confirming securely…' : charger.available ? 'Confirm booking' : 'Currently unavailable'}
          </button>
        </div>
      </section>
    </div>
  );
}
