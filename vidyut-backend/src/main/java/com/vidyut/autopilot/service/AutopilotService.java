package com.vidyut.autopilot.service;

import com.vidyut.autopilot.dto.AutopilotActionResponse;
import com.vidyut.autopilot.dto.AutopilotProgressRequest;
import com.vidyut.autopilot.dto.AutopilotStopResponse;
import com.vidyut.autopilot.dto.AutopilotTelemetryResponse;
import com.vidyut.autopilot.dto.AutopilotTripRequest;
import com.vidyut.autopilot.dto.AutopilotTripResponse;
import com.vidyut.autopilot.entity.AutopilotAction;
import com.vidyut.autopilot.entity.AutopilotActionState;
import com.vidyut.autopilot.entity.AutopilotStop;
import com.vidyut.autopilot.entity.AutopilotStopStatus;
import com.vidyut.autopilot.entity.AutopilotTrip;
import com.vidyut.autopilot.entity.AutopilotTripStatus;
import com.vidyut.autopilot.repository.AutopilotActionRepository;
import com.vidyut.autopilot.repository.AutopilotStopRepository;
import com.vidyut.autopilot.repository.AutopilotTripRepository;
import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.service.BookingService;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.payment.dto.PaymentRequest;
import com.vidyut.payment.dto.PaymentResponse;
import com.vidyut.payment.service.PaymentService;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.dto.AutoRechargeRuleResponse;
import com.vidyut.wallet.dto.WalletResponse;
import com.vidyut.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutopilotService {

    private static final double ENERGY_PER_KM_KWH = 0.12;
    private static final double ROAD_DISTANCE_FACTOR = 1.12;
    private static final int MAX_STOPS = 5;

    private static final Map<String, GeoPoint> CITY_COORDINATES = Map.ofEntries(
            Map.entry("kanpur", new GeoPoint(26.4499, 80.3319)),
            Map.entry("lucknow", new GeoPoint(26.8467, 80.9462)),
            Map.entry("etawah", new GeoPoint(26.7829, 79.0277)),
            Map.entry("agra", new GeoPoint(27.1767, 78.0081)),
            Map.entry("mathura", new GeoPoint(27.4924, 77.6737)),
            Map.entry("greater noida", new GeoPoint(28.4744, 77.5040)),
            Map.entry("noida", new GeoPoint(28.5355, 77.3910)),
            Map.entry("delhi", new GeoPoint(28.6139, 77.2090)),
            Map.entry("gurugram", new GeoPoint(28.4595, 77.0266))
    );

    private final AutopilotTripRepository tripRepository;
    private final AutopilotStopRepository stopRepository;
    private final AutopilotActionRepository actionRepository;
    private final VehicleRepository vehicleRepository;
    private final ChargingStationRepository stationRepository;
    private final BookingService bookingService;
    private final WalletService walletService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    @Transactional
    public AutopilotTripResponse launchTrip(Long userId, AutopilotTripRequest request) {
        String idempotencyKey = normalizedIdempotencyKey(request.getIdempotencyKey());
        var existing = tripRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Vehicle vehicle = ownedVehicle(request.getVehicleId(), userId);
        validateConstraints(request);

        GeoPoint origin = resolveLocation(request.getOrigin(), CITY_COORDINATES.get("kanpur"));
        GeoPoint destination = resolveLocation(request.getDestination(), CITY_COORDINATES.get("delhi"));
        double routeDistance = Math.max(20, haversine(origin, destination) * ROAD_DISTANCE_FACTOR);
        double capacityKwh = batteryCapacity(vehicle);
        String optimization = normalizedOptimization(request.getOptimizeFor());
        List<Candidate> candidates = compatibleCandidates(
                vehicle, origin, destination, routeDistance, capacityKwh, optimization);
        List<Candidate> selected = selectReachableStops(
                candidates,
                routeDistance,
                capacityKwh,
                request.getCurrentBatteryPercent(),
                request.getMinimumArrivalBatteryPercent()
        );

        AutopilotTrip trip = tripRepository.save(AutopilotTrip.builder()
                .userId(userId)
                .vehicleId(vehicle.getId())
                .idempotencyKey(idempotencyKey)
                .goal(normalizedGoal(request))
                .origin(request.getOrigin().trim())
                .destination(request.getDestination().trim())
                .arrivalDeadline(blankToNull(request.getArrivalDeadline()))
                .optimizeFor(optimization)
                .startingBatteryPercent(round(request.getCurrentBatteryPercent()))
                .currentBatteryPercent(round(request.getCurrentBatteryPercent()))
                .minimumArrivalBatteryPercent(round(request.getMinimumArrivalBatteryPercent()))
                .maximumChargingBudget(roundMoney(request.getMaximumChargingBudget()))
                .totalDistanceKm(round(routeDistance))
                .estimatedDriveMinutes((int) Math.ceil(routeDistance / 68.0 * 60))
                .estimatedArrivalBatteryPercent(round(request.getMinimumArrivalBatteryPercent() + 4))
                .status(AutopilotTripStatus.RESERVED)
                .build());

        List<AutopilotStop> stops = buildStops(trip, selected, capacityKwh);
        double totalCost = stops.stream().mapToDouble(AutopilotStop::getEstimatedCost).sum();
        if (totalCost > request.getMaximumChargingBudget()) {
            throw new BadRequestException("No safe route fits the ₹" + roundMoney(request.getMaximumChargingBudget())
                    + " charging budget. Increase the budget to at least ₹" + Math.ceil(totalCost) + ".");
        }
        stopRepository.saveAll(stops);

        int chargingMinutes = stops.stream()
                .mapToInt(stop -> stop.getChargingMinutes() + stop.getEstimatedWaitMinutes())
                .sum();
        trip.setEstimatedChargingCost(roundMoney(totalCost));
        trip.setTotalDurationMinutes(trip.getEstimatedDriveMinutes() + chargingMinutes);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        addAction(trip, AutopilotActionState.SUCCESS, "Vehicle connected",
                vehicle.getMakeAndModel() + " · " + round(request.getCurrentBatteryPercent())
                        + "% battery · " + vehicle.getConnectorType());
        addAction(trip, AutopilotActionState.SUCCESS, "Goal understood",
                "Arrival by " + displayDeadline(trip.getArrivalDeadline()) + ", reserve "
                        + round(trip.getMinimumArrivalBatteryPercent()) + "%, budget ₹"
                        + roundMoney(trip.getMaximumChargingBudget()) + ".");
        addAction(trip, AutopilotActionState.SUCCESS, "Route analyzed",
                candidates.size() + " compatible charging options scored by travel impact, queue, power and price.");
        addAction(trip, AutopilotActionState.INFO, "Trip plan created",
                stops.size() + " safe charging stop" + (stops.size() == 1 ? "" : "s")
                        + " · estimated charging ₹" + roundMoney(totalCost) + ".");

        reserveNextStop(trip, stops.get(0), userId);
        notificationService.sendNotification(userId, "Vidyut Autopilot is ready",
                "Your first charger is reserved. Vidyut is monitoring the journey to " + trip.getDestination() + ".",
                NotificationType.BOOKING_CONFIRMED);
        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public AutopilotTripResponse getCurrentTrip(Long userId) {
        return tripRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public AutopilotTripResponse getTrip(Long tripId, Long userId) {
        return toResponse(ownedTrip(tripId, userId));
    }

    @Transactional
    public AutopilotTripResponse startJourney(Long tripId, Long userId, AutopilotProgressRequest request) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        if (trip.getStatus() == AutopilotTripStatus.COMPLETED) return toResponse(trip);

        double drop = request == null || request.getBatteryDropPercent() <= 0
                ? 6
                : request.getBatteryDropPercent();
        trip.setCurrentBatteryPercent(round(Math.max(
                trip.getMinimumArrivalBatteryPercent() + 2,
                trip.getCurrentBatteryPercent() - drop
        )));
        trip.setStatus(AutopilotTripStatus.MONITORING);
        trip.setPaymentMessage(null);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);
        addAction(trip, AutopilotActionState.SUCCESS, "Navigation started",
                "Live telemetry active · battery " + trip.getCurrentBatteryPercent()
                        + "% · next reservation protected.");
        addAction(trip, AutopilotActionState.INFO, "Journey monitored",
                "Vidyut is watching charger status, queue changes, battery safety and budget.");
        return toResponse(trip);
    }

    @Transactional
    public AutopilotTripResponse simulateChargerFault(Long tripId, Long userId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        if (trip.getStatus() == AutopilotTripStatus.COMPLETED) {
            throw new BadRequestException("A completed trip cannot be rerouted");
        }

        AutopilotStop current = stopRepository
                .findFirstByTripIdAndStatusOrderBySequenceNumberAsc(tripId, AutopilotStopStatus.RESERVED)
                .orElseThrow(() -> new BadRequestException("This trip has no active charger reservation"));

        addAction(trip, AutopilotActionState.WARNING, "Station fault detected",
                current.getStationName() + " stopped responding. Replanning started automatically.");

        if (current.getBookingId() != null) {
            bookingService.cancelBooking(current.getBookingId(), userId);
        }
        current.setStatus(AutopilotStopStatus.CANCELLED);
        stopRepository.save(current);
        addAction(trip, AutopilotActionState.SUCCESS, "Old reservation released",
                "Booking #" + current.getBookingId() + " cancelled without creating a duplicate charge.");

        Vehicle vehicle = ownedVehicle(trip.getVehicleId(), userId);
        GeoPoint origin = resolveLocation(trip.getOrigin(), CITY_COORDINATES.get("kanpur"));
        GeoPoint destination = resolveLocation(trip.getDestination(), CITY_COORDINATES.get("delhi"));
        double capacityKwh = batteryCapacity(vehicle);
        List<Candidate> candidates = compatibleCandidates(
                vehicle,
                origin,
                destination,
                trip.getTotalDistanceKm(),
                capacityKwh,
                trip.getOptimizeFor()
        );
        Set<Long> usedStationIds = new HashSet<>();
        stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId)
                .forEach(stop -> usedStationIds.add(stop.getStationId()));

        Candidate replacement = candidates.stream()
                .filter(candidate -> !usedStationIds.contains(candidate.station().getId()))
                .min(Comparator.comparingDouble(candidate ->
                        Math.abs(candidate.distanceFromOriginKm() - current.getDistanceFromOriginKm()) * 1.8
                                + candidate.impactMinutes()))
                .orElseThrow(() -> new BadRequestException("No compatible replacement charger is currently available"));

        AutopilotStop replacementStop = createReplacementStop(trip, current, replacement, capacityKwh);
        replacementStop = stopRepository.save(replacementStop);
        reserveNextStop(trip, replacementStop, userId);

        trip.setEstimatedChargingCost(roundMoney(
                Math.max(0, trip.getEstimatedChargingCost() - current.getEstimatedCost())
                        + replacementStop.getEstimatedCost()
        ));
        trip.setTotalDurationMinutes(Math.max(
                trip.getEstimatedDriveMinutes(),
                trip.getTotalDurationMinutes() - current.getEstimatedWaitMinutes() - current.getChargingMinutes()
                        + replacementStop.getEstimatedWaitMinutes() + replacementStop.getChargingMinutes()
        ));
        trip.setStatus(AutopilotTripStatus.REROUTED);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        addAction(trip, AutopilotActionState.SUCCESS, "Alternative selected",
                replacementStop.getStationName() + " · " + replacementStop.getConnectorType() + " · "
                        + round(replacementStop.getPowerKw()) + " kW.");
        addAction(trip, AutopilotActionState.SUCCESS, "Booking transferred",
                "Connector reserved under booking #" + replacementStop.getBookingId() + ".");
        addAction(trip, AutopilotActionState.SUCCESS, "Route updated",
                "New charging estimate ₹" + roundMoney(trip.getEstimatedChargingCost())
                        + " · arrival reserve remains above " + round(trip.getMinimumArrivalBatteryPercent()) + "%.");
        notificationService.sendNotification(userId, "Route automatically updated",
                "Your charger became unavailable. " + replacementStop.getStationName()
                        + " is reserved and navigation has been updated.", NotificationType.FAULT_ALERT);
        return toResponse(trip);
    }

    @Transactional
    public AutopilotTripResponse completeCharging(Long tripId, Long userId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        if (trip.getStatus() == AutopilotTripStatus.COMPLETED) return toResponse(trip);

        AutopilotStop activeStop = stopRepository
                .findFirstByTripIdAndStatusOrderBySequenceNumberAsc(tripId, AutopilotStopStatus.RESERVED)
                .orElseThrow(() -> new BadRequestException("This trip has no active charging reservation"));

        WalletResponse wallet = walletService.getWalletByUserId(userId);
        List<AutoRechargeRuleResponse> rules = walletService.getAutoRechargeRules(userId);
        boolean canAutoRecharge = rules.stream()
                .filter(rule -> rule.getVehicleId().equals(trip.getVehicleId()) && rule.isEnabled())
                .anyMatch(rule -> wallet.getBalance() + rule.getRechargeAmount() >= activeStop.getEstimatedCost());

        if (wallet.getBalance() < activeStop.getEstimatedCost() && !canAutoRecharge) {
            trip.setStatus(AutopilotTripStatus.PAYMENT_REQUIRED);
            trip.setPaymentMessage("Add ₹" + Math.ceil(activeStop.getEstimatedCost() - wallet.getBalance())
                    + " or enable vehicle auto-recharge to finish AutoPay.");
            trip.setUpdatedAt(LocalDateTime.now());
            tripRepository.save(trip);
            addAction(trip, AutopilotActionState.WARNING, "AutoPay needs attention", trip.getPaymentMessage());
            return toResponse(trip);
        }

        PaymentResponse payment = paymentService.processPayment(userId, PaymentRequest.builder()
                .bookingId(activeStop.getBookingId())
                .amount(activeStop.getEstimatedCost())
                .paymentMethod("VIDYUT_WALLET_AUTOPAY")
                .build());

        activeStop.setStatus(AutopilotStopStatus.COMPLETED);
        stopRepository.save(activeStop);
        trip.setCurrentBatteryPercent(round(activeStop.getTargetBatteryPercent()));
        trip.setStatus(AutopilotTripStatus.COMPLETED);
        trip.setPaymentMessage("Paid with Vidyut AutoPay · " + payment.getGatewayTransactionId());
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        addAction(trip, AutopilotActionState.SUCCESS, "Charging completed",
                round(activeStop.getTargetBatteryPercent()) + "% battery · connector released.");
        addAction(trip, AutopilotActionState.SUCCESS, "Wallet paid automatically",
                "₹" + roundMoney(activeStop.getEstimatedCost()) + " paid · " + payment.getGatewayTransactionId() + ".");
        addAction(trip, AutopilotActionState.SUCCESS, "Journey continues",
                "Navigation resumed with the planned battery safety reserve.");
        notificationService.sendNotification(userId, "Charging and AutoPay complete",
                "₹" + roundMoney(activeStop.getEstimatedCost()) + " paid at " + activeStop.getStationName() + ".",
                NotificationType.CHARGING_COMPLETED);
        return toResponse(trip);
    }

    private void validateConstraints(AutopilotTripRequest request) {
        if (request.getCurrentBatteryPercent() <= request.getMinimumArrivalBatteryPercent()) {
            throw new BadRequestException("Current battery must be above the requested arrival reserve");
        }
    }

    private List<Candidate> compatibleCandidates(
            Vehicle vehicle,
            GeoPoint origin,
            GeoPoint destination,
            double routeDistance,
            double capacityKwh,
            String optimizeFor
    ) {
        List<Candidate> onRoute = new ArrayList<>();
        List<Candidate> allCompatible = new ArrayList<>();

        for (ChargingStation station : stationRepository.findAll()) {
            if (station.getStatus() != StationStatus.ACTIVE
                    || station.getAvailability() == StationAvailability.UNAVAILABLE
                    || station.isEmergencyDisabled()) {
                continue;
            }
            ChargingConnector connector = bestConnector(station, vehicle.getConnectorType());
            if (connector == null) continue;

            GeoPoint stationPoint = new GeoPoint(station.getLatitude(), station.getLongitude());
            double fromOrigin = haversine(origin, stationPoint) * ROAD_DISTANCE_FACTOR;
            double detour = Math.max(0,
                    (haversine(origin, stationPoint) + haversine(stationPoint, destination))
                            * ROAD_DISTANCE_FACTOR - routeDistance);
            int waitMinutes = Math.max(0, station.getQueueCount() * 7)
                    + (int) Math.round(station.getOccupancyPercent() / 20.0 * 3);
            int sampleChargeMinutes = Math.max(8,
                    (int) Math.ceil(capacityKwh * 0.35 / Math.max(7.4, connector.getPowerKw()) * 60) + 3);
            double reliabilityPenalty = Math.max(0, 5 - station.getRating()) * 8;
            double timeImpact = waitMinutes + sampleChargeMinutes + detour * 0.65 + reliabilityPenalty;
            double priceImpact = station.getPricePerKwh() * 2.2;
            double impact = switch (optimizeFor) {
                case "COST" -> timeImpact * 0.45 + priceImpact * 1.8;
                case "BALANCED" -> timeImpact * 0.8 + priceImpact;
                default -> timeImpact + priceImpact * 0.25;
            };
            Candidate candidate = new Candidate(station, connector, round(fromOrigin), round(detour), waitMinutes, impact);
            allCompatible.add(candidate);
            if (fromOrigin > 10 && fromOrigin < routeDistance - 5 && detour <= Math.max(90, routeDistance * 0.22)) {
                onRoute.add(candidate);
            }
        }

        List<Candidate> result = onRoute.isEmpty() ? allCompatible : onRoute;
        if (result.isEmpty()) {
            throw new BadRequestException("No online charger matches " + vehicle.getConnectorType() + " for this route");
        }
        return result.stream()
                .sorted(Comparator.comparingDouble(Candidate::distanceFromOriginKm))
                .toList();
    }

    private List<Candidate> selectReachableStops(
            List<Candidate> candidates,
            double routeDistance,
            double capacityKwh,
            double startingBattery,
            double minimumBattery
    ) {
        List<Candidate> selected = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        double marker = 0;
        double availableBattery = startingBattery;

        while (selected.size() < MAX_STOPS) {
            double safeRange = capacityKwh * Math.max(0, availableBattery - minimumBattery)
                    / 100.0 / ENERGY_PER_KM_KWH;
            if (routeDistance - marker <= safeRange) break;

            double maximumMarker = marker + safeRange * 0.92;
            double minimumMarker = marker + Math.min(12, safeRange * 0.2);
            List<Candidate> reachable = candidates.stream()
                    .filter(candidate -> !used.contains(candidate.station().getId()))
                    .filter(candidate -> candidate.distanceFromOriginKm() > minimumMarker)
                    .filter(candidate -> candidate.distanceFromOriginKm() <= maximumMarker)
                    .toList();

            if (reachable.isEmpty()) {
                throw new BadRequestException("No compatible charger is safely reachable before the "
                        + round(minimumBattery) + "% battery reserve");
            }

            double furthestMarker = reachable.stream()
                    .mapToDouble(Candidate::distanceFromOriginKm)
                    .max()
                    .orElseThrow();
            Candidate chosen = reachable.stream()
                    .filter(candidate -> candidate.distanceFromOriginKm() >= furthestMarker - 35)
                    .min(Comparator.comparingDouble(Candidate::impactMinutes))
                    .orElseThrow();
            selected.add(chosen);
            used.add(chosen.station().getId());
            marker = chosen.distanceFromOriginKm();
            availableBattery = 80;
        }

        if (routeDistance - marker > capacityKwh * (80 - minimumBattery) / 100.0 / ENERGY_PER_KM_KWH) {
            throw new BadRequestException("A safe route could not be produced with the available charging network");
        }
        if (selected.isEmpty()) {
            Candidate convenienceStop = candidates.stream()
                    .min(Comparator.comparingDouble(candidate ->
                            Math.abs(candidate.distanceFromOriginKm() - routeDistance * 0.55)
                                    + candidate.impactMinutes()))
                    .orElseThrow();
            selected.add(convenienceStop);
        }
        return selected;
    }

    private List<AutopilotStop> buildStops(AutopilotTrip trip, List<Candidate> selected, double capacityKwh) {
        List<AutopilotStop> stops = new ArrayList<>();
        double previousMarker = 0;
        double previousBattery = trip.getStartingBatteryPercent();

        for (int index = 0; index < selected.size(); index++) {
            Candidate candidate = selected.get(index);
            double arrivalBattery = Math.max(
                    trip.getMinimumArrivalBatteryPercent() + 1,
                    previousBattery - (candidate.distanceFromOriginKm() - previousMarker)
                            * ENERGY_PER_KM_KWH / capacityKwh * 100
            );
            double nextMarker = index + 1 < selected.size()
                    ? selected.get(index + 1).distanceFromOriginKm()
                    : trip.getTotalDistanceKm();
            double batteryNeeded = (nextMarker - candidate.distanceFromOriginKm())
                    * ENERGY_PER_KM_KWH / capacityKwh * 100;
            double targetBattery = Math.min(80,
                    Math.max(arrivalBattery + 8, trip.getMinimumArrivalBatteryPercent() + 4 + batteryNeeded));
            double energyAdded = capacityKwh * (targetBattery - arrivalBattery) / 100.0;
            int chargingMinutes = Math.max(6,
                    (int) Math.ceil(energyAdded / Math.max(7.4, candidate.connector().getPowerKw()) * 60) + 3);

            stops.add(AutopilotStop.builder()
                    .tripId(trip.getId())
                    .sequenceNumber(index + 1)
                    .stationId(candidate.station().getId())
                    .stationName(candidate.station().getName())
                    .stationAddress(candidate.station().getAddress())
                    .connectorType(candidate.connector().getType().name())
                    .powerKw(round(candidate.connector().getPowerKw()))
                    .distanceFromOriginKm(candidate.distanceFromOriginKm())
                    .arrivalBatteryPercent(round(arrivalBattery))
                    .targetBatteryPercent(round(targetBattery))
                    .estimatedWaitMinutes(candidate.waitMinutes())
                    .chargingMinutes(chargingMinutes)
                    .estimatedCost(roundMoney(energyAdded * candidate.station().getPricePerKwh()))
                    .status(AutopilotStopStatus.PLANNED)
                    .build());
            previousMarker = candidate.distanceFromOriginKm();
            previousBattery = targetBattery;
        }
        return stops;
    }

    private AutopilotStop createReplacementStop(
            AutopilotTrip trip,
            AutopilotStop replaced,
            Candidate candidate,
            double capacityKwh
    ) {
        double arrival = Math.max(trip.getMinimumArrivalBatteryPercent() + 2, trip.getCurrentBatteryPercent() - 4);
        double target = Math.max(arrival + 10, Math.min(80, replaced.getTargetBatteryPercent()));
        double energy = capacityKwh * (target - arrival) / 100.0;
        int nextSequence = stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(trip.getId()).stream()
                .mapToInt(AutopilotStop::getSequenceNumber)
                .max()
                .orElse(0) + 1;
        return AutopilotStop.builder()
                .tripId(trip.getId())
                .sequenceNumber(nextSequence)
                .stationId(candidate.station().getId())
                .stationName(candidate.station().getName())
                .stationAddress(candidate.station().getAddress())
                .connectorType(candidate.connector().getType().name())
                .powerKw(round(candidate.connector().getPowerKw()))
                .distanceFromOriginKm(candidate.distanceFromOriginKm())
                .arrivalBatteryPercent(round(arrival))
                .targetBatteryPercent(round(target))
                .estimatedWaitMinutes(candidate.waitMinutes())
                .chargingMinutes(Math.max(6,
                        (int) Math.ceil(energy / Math.max(7.4, candidate.connector().getPowerKw()) * 60) + 3))
                .estimatedCost(roundMoney(energy * candidate.station().getPricePerKwh()))
                .status(AutopilotStopStatus.PLANNED)
                .build();
    }

    private void reserveNextStop(AutopilotTrip trip, AutopilotStop stop, Long userId) {
        BookingResponse booking = bookingService.createBooking(BookingCreateRequest.builder()
                .stationId(stop.getStationId())
                .vehicleId(trip.getVehicleId())
                .startTime(LocalDateTime.now().plusMinutes(Math.max(15, trip.getEstimatedDriveMinutes() / 3)))
                .durationHours(Math.max(1, (int) Math.ceil(stop.getChargingMinutes() / 60.0)))
                .build(), userId);
        stop.setBookingId(booking.getId());
        stop.setStatus(AutopilotStopStatus.RESERVED);
        stopRepository.save(stop);
        trip.setActiveStationId(stop.getStationId());
        trip.setActiveBookingId(booking.getId());
        trip.setPaymentMessage("Wallet authorization will complete after charging.");
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);
        addAction(trip, AutopilotActionState.SUCCESS, "Connector reserved",
                stop.getStationName() + " · " + stop.getConnectorType() + " · booking #" + booking.getId() + ".");
    }

    private ChargingConnector bestConnector(ChargingStation station, String vehicleConnector) {
        String normalized = normalizeConnector(vehicleConnector);
        return station.getConnectors().stream()
                .filter(ChargingConnector::isAvailable)
                .filter(connector -> !connector.isMaintenanceMode())
                .filter(connector -> connector.getStatus() == ChargerStatus.ONLINE)
                .filter(connector -> normalized.contains(connector.getType().name())
                        || connector.getType().name().contains(normalized))
                .max(Comparator.comparingDouble(ChargingConnector::getPowerKw))
                .orElse(null);
    }

    private String normalizeConnector(String connector) {
        if (connector == null) return "CCS2";
        String normalized = connector.toUpperCase(Locale.ROOT)
                .replace("TYPE 2", "TYPE2")
                .replace("CHADEMO", "CHADEMO")
                .replaceAll("[^A-Z0-9/]", "");
        return normalized.isBlank() ? "CCS2" : normalized;
    }

    private Vehicle ownedVehicle(Long vehicleId, Long userId) {
        return vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this account"));
    }

    private AutopilotTrip ownedTrip(Long tripId, Long userId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Autopilot trip not found for this account"));
    }

    private void addAction(AutopilotTrip trip, AutopilotActionState state, String title, String detail) {
        int sequence = (int) actionRepository.countByTripId(trip.getId()) + 1;
        actionRepository.save(AutopilotAction.builder()
                .tripId(trip.getId())
                .sequenceNumber(sequence)
                .state(state)
                .title(title)
                .detail(detail)
                .build());
    }

    private AutopilotTripResponse toResponse(AutopilotTrip trip) {
        Vehicle vehicle = vehicleRepository.findByIdAndUserId(trip.getVehicleId(), trip.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this trip"));
        double capacity = batteryCapacity(vehicle);
        WalletResponse wallet = walletService.getWalletByUserId(trip.getUserId());
        return AutopilotTripResponse.builder()
                .id(trip.getId())
                .idempotencyKey(trip.getIdempotencyKey())
                .goal(trip.getGoal())
                .origin(trip.getOrigin())
                .destination(trip.getDestination())
                .arrivalDeadline(trip.getArrivalDeadline())
                .optimizeFor(trip.getOptimizeFor())
                .minimumArrivalBatteryPercent(trip.getMinimumArrivalBatteryPercent())
                .maximumChargingBudget(trip.getMaximumChargingBudget())
                .totalDistanceKm(trip.getTotalDistanceKm())
                .estimatedDriveMinutes(trip.getEstimatedDriveMinutes())
                .totalDurationMinutes(trip.getTotalDurationMinutes())
                .estimatedChargingCost(trip.getEstimatedChargingCost())
                .estimatedArrivalBatteryPercent(trip.getEstimatedArrivalBatteryPercent())
                .activeStationId(trip.getActiveStationId())
                .activeBookingId(trip.getActiveBookingId())
                .status(trip.getStatus())
                .paymentMessage(trip.getPaymentMessage())
                .walletBalance(roundMoney(wallet.getBalance()))
                .telemetry(AutopilotTelemetryResponse.builder()
                        .vehicleId(vehicle.getId())
                        .vehicleName(vehicle.getMakeAndModel())
                        .registrationNumber(vehicle.getRegistrationNumber())
                        .connectorType(vehicle.getConnectorType())
                        .batteryCapacityKwh(capacity)
                        .batteryPercent(trip.getCurrentBatteryPercent())
                        .remainingRangeKm(round(capacity * trip.getCurrentBatteryPercent() / 100.0 / ENERGY_PER_KM_KWH))
                        .state(telemetryState(trip.getStatus()))
                        .build())
                .stops(stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(trip.getId()).stream()
                        .map(this::mapStop)
                        .toList())
                .timeline(actionRepository.findByTripIdOrderBySequenceNumberAsc(trip.getId()).stream()
                        .map(this::mapAction)
                        .toList())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .build();
    }

    private AutopilotStopResponse mapStop(AutopilotStop stop) {
        return AutopilotStopResponse.builder()
                .id(stop.getId())
                .sequenceNumber(stop.getSequenceNumber())
                .stationId(stop.getStationId())
                .bookingId(stop.getBookingId())
                .stationName(stop.getStationName())
                .stationAddress(stop.getStationAddress())
                .connectorType(stop.getConnectorType())
                .powerKw(stop.getPowerKw())
                .distanceFromOriginKm(stop.getDistanceFromOriginKm())
                .arrivalBatteryPercent(stop.getArrivalBatteryPercent())
                .targetBatteryPercent(stop.getTargetBatteryPercent())
                .estimatedWaitMinutes(stop.getEstimatedWaitMinutes())
                .chargingMinutes(stop.getChargingMinutes())
                .estimatedCost(stop.getEstimatedCost())
                .status(stop.getStatus())
                .build();
    }

    private AutopilotActionResponse mapAction(AutopilotAction action) {
        return AutopilotActionResponse.builder()
                .sequenceNumber(action.getSequenceNumber())
                .state(action.getState())
                .title(action.getTitle())
                .detail(action.getDetail())
                .timestamp(action.getTimestamp())
                .build();
    }

    private double batteryCapacity(Vehicle vehicle) {
        if (vehicle.getBatteryCapacity() == null) return 40.5;
        String numeric = vehicle.getBatteryCapacity().replaceAll("[^0-9.]", "");
        try {
            double parsed = Double.parseDouble(numeric);
            return parsed > 10 && parsed < 250 ? parsed : 40.5;
        } catch (NumberFormatException ignored) {
            return 40.5;
        }
    }

    private GeoPoint resolveLocation(String value, GeoPoint fallback) {
        if (value != null) {
            String normalized = value.toLowerCase(Locale.ROOT).trim();
            for (Map.Entry<String, GeoPoint> entry : CITY_COORDINATES.entrySet()) {
                if (normalized.contains(entry.getKey())) return entry.getValue();
            }
        }
        return fallback;
    }

    private String normalizedGoal(AutopilotTripRequest request) {
        if (request.getGoal() != null && !request.getGoal().isBlank()) return request.getGoal().trim();
        return "Get me from " + request.getOrigin().trim() + " to " + request.getDestination().trim()
                + " by " + displayDeadline(request.getArrivalDeadline()) + ". Keep charging under ₹"
                + roundMoney(request.getMaximumChargingBudget()) + " and battery above "
                + round(request.getMinimumArrivalBatteryPercent()) + "%.";
    }

    private String normalizedOptimization(String optimizeFor) {
        if (optimizeFor == null || optimizeFor.isBlank()) return "TIME";
        String normalized = optimizeFor.trim().toUpperCase(Locale.ROOT);
        return Set.of("TIME", "COST", "BALANCED").contains(normalized) ? normalized : "TIME";
    }

    private String normalizedIdempotencyKey(String key) {
        return key == null || key.isBlank() ? UUID.randomUUID().toString() : key.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String displayDeadline(String value) {
        return value == null || value.isBlank() ? "the requested time" : value;
    }

    private String telemetryState(AutopilotTripStatus status) {
        return switch (status) {
            case RESERVED -> "READY";
            case MONITORING, REROUTED -> "DRIVING";
            case PAYMENT_REQUIRED -> "CHARGING_COMPLETE";
            case COMPLETED -> "TRIP_CONTINUING";
            case CANCELLED -> "STOPPED";
        };
    }

    private double haversine(GeoPoint first, GeoPoint second) {
        double earthRadiusKm = 6371;
        double latitudeDelta = Math.toRadians(second.latitude() - first.latitude());
        double longitudeDelta = Math.toRadians(second.longitude() - first.longitude());
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(first.latitude())) * Math.cos(Math.toRadians(second.latitude()))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record GeoPoint(double latitude, double longitude) {}

    private record Candidate(
            ChargingStation station,
            ChargingConnector connector,
            double distanceFromOriginKm,
            double detourKm,
            int waitMinutes,
            double impactMinutes
    ) {}
}
