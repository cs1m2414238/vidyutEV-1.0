package com.vidyut.booking.service;

import com.vidyut.booking.entity.Booking;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingStation;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared read-only capacity rules for proposals and the locked booking transaction. */
public final class BookingAvailability {
    private BookingAvailability() {}

    public static String conflict(ChargingStation station, Long connectorId, List<Booking> overlaps) {
        Set<Long> healthy = station.getConnectors().stream()
                .filter(c -> c.isAvailable() && !c.isMaintenanceMode() && c.getStatus() == ChargerStatus.ONLINE)
                .map(c -> c.getId()).collect(Collectors.toSet());
        if (connectorId != null && overlaps.stream().anyMatch(b -> Objects.equals(b.getConnectorId(), connectorId)))
            return "The exact recovery connector is already reserved for this time. Evaluate recovery again.";
        // An exact reservation on failed hardware remains intact until approval,
        // but cannot consume the capacity of a different, healthy connector.
        long occupied = overlaps.stream().filter(b -> b.getConnectorId() == null || healthy.contains(b.getConnectorId())).count();
        if (occupied >= healthy.size())
            return "The selected charging slot is full. Join the waitlist or choose another time.";
        return null;
    }
}
