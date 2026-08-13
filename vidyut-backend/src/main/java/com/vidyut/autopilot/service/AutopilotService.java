package com.vidyut.autopilot.service;

import com.vidyut.autopilot.dto.AutopilotActionResponse;
import com.vidyut.autopilot.dto.AutopilotPlanResponse;
import com.vidyut.autopilot.dto.AutopilotPlanStopResponse;
import com.vidyut.autopilot.dto.AutopilotProgressRequest;
import com.vidyut.autopilot.dto.AutopilotStopResponse;
import com.vidyut.autopilot.dto.AutopilotTelemetryResponse;
import com.vidyut.autopilot.dto.AutopilotTripRequest;
import com.vidyut.autopilot.dto.AutopilotTripResponse;
import com.vidyut.autopilot.dto.AutopilotTripSummaryResponse;
import com.vidyut.autopilot.dto.RouteExperienceRequest;
import com.vidyut.autopilot.dto.RouteExperienceResponse;
import com.vidyut.autopilot.entity.AutopilotAction;
import com.vidyut.autopilot.entity.AutopilotActionState;
import com.vidyut.autopilot.entity.AutopilotStop;
import com.vidyut.autopilot.entity.AutopilotStopStatus;
import com.vidyut.autopilot.entity.AutopilotTrip;
import com.vidyut.autopilot.entity.AutopilotTripStatus;
import com.vidyut.autopilot.entity.RouteExperience;
import com.vidyut.autopilot.entity.RouteExperienceOutcome;
import com.vidyut.autopilot.entity.TripPurpose;
import com.vidyut.autopilot.repository.AutopilotActionRepository;
import com.vidyut.autopilot.repository.AutopilotStopRepository;
import com.vidyut.autopilot.repository.AutopilotTripRepository;
import com.vidyut.autopilot.repository.RouteExperienceRepository;
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
import com.vidyut.routing.dto.RouteStationResponse;
import com.vidyut.routing.service.RoutingService;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.dto.AutoRechargeRuleResponse;
import com.vidyut.wallet.dto.WalletResponse;
import com.vidyut.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    // A cross-country route such as Srinagar -> Kanyakumari needs more than five
    // safe charging legs. Keep a finite guard, but allow the nationwide demo
    // network to build realistic long-distance plans.
    private static final int MAX_STOPS = 20;

    private static final Map<String, GeoPoint> CITY_COORDINATES = Map.ofEntries(
            Map.entry("leh", new GeoPoint(34.1526, 77.5771)),
            Map.entry("keylong", new GeoPoint(32.5715, 77.0243)),
            Map.entry("manali", new GeoPoint(32.2432, 77.1892)),
            Map.entry("kargil", new GeoPoint(34.5539, 76.1349)),
            Map.entry("srinagar", new GeoPoint(34.0837, 74.7973)),
            Map.entry("jammu", new GeoPoint(32.7266, 74.8570)),
            Map.entry("pathankot", new GeoPoint(32.2643, 75.6421)),
            Map.entry("jalandhar", new GeoPoint(31.3260, 75.5762)),
            Map.entry("ludhiana", new GeoPoint(30.9010, 75.8573)),
            Map.entry("chandigarh", new GeoPoint(30.7333, 76.7794)),
            Map.entry("ambala", new GeoPoint(30.3782, 76.7767)),
            Map.entry("karnal", new GeoPoint(29.6857, 76.9905)),
            Map.entry("panipat", new GeoPoint(29.3909, 76.9635)),
            Map.entry("shimla", new GeoPoint(31.1048, 77.1734)),
            Map.entry("dehradun", new GeoPoint(30.3165, 78.0322)),
            Map.entry("haridwar", new GeoPoint(29.9457, 78.1642)),
            Map.entry("kanpur", new GeoPoint(26.4499, 80.3319)),
            Map.entry("lucknow", new GeoPoint(26.8467, 80.9462)),
            Map.entry("prayagraj", new GeoPoint(25.4358, 81.8463)),
            Map.entry("varanasi", new GeoPoint(25.3176, 82.9739)),
            Map.entry("gorakhpur", new GeoPoint(26.7606, 83.3732)),
            Map.entry("etawah", new GeoPoint(26.7829, 79.0277)),
            Map.entry("agra", new GeoPoint(27.1767, 78.0081)),
            Map.entry("mathura", new GeoPoint(27.4924, 77.6737)),
            Map.entry("greater noida", new GeoPoint(28.4744, 77.5040)),
            Map.entry("noida", new GeoPoint(28.5355, 77.3910)),
            Map.entry("delhi", new GeoPoint(28.6139, 77.2090)),
            Map.entry("gurugram", new GeoPoint(28.4595, 77.0266)),
            Map.entry("rewari", new GeoPoint(28.1920, 76.6191)),
            Map.entry("jaipur", new GeoPoint(26.9124, 75.7873)),
            Map.entry("kishangarh", new GeoPoint(26.5906, 74.8564)),
            Map.entry("ajmer", new GeoPoint(26.4499, 74.6399)),
            Map.entry("jodhpur", new GeoPoint(26.2389, 73.0243)),
            Map.entry("jaisalmer", new GeoPoint(26.9157, 70.9083)),
            Map.entry("kota", new GeoPoint(25.2138, 75.8648)),
            Map.entry("udaipur", new GeoPoint(24.5854, 73.7125)),
            Map.entry("ahmedabad", new GeoPoint(23.0225, 72.5714)),
            Map.entry("rajkot", new GeoPoint(22.3039, 70.8022)),
            Map.entry("gandhidham", new GeoPoint(23.0753, 70.1337)),
            Map.entry("bhuj", new GeoPoint(23.2419, 69.6669)),
            Map.entry("vadodara", new GeoPoint(22.3072, 73.1812)),
            Map.entry("surat", new GeoPoint(21.1702, 72.8311)),
            Map.entry("nashik", new GeoPoint(19.9975, 73.7898)),
            Map.entry("mumbai", new GeoPoint(19.0760, 72.8777)),
            Map.entry("pune", new GeoPoint(18.5204, 73.8567)),
            Map.entry("kolhapur", new GeoPoint(16.7050, 74.2433)),
            Map.entry("goa", new GeoPoint(15.4909, 73.8278)),
            Map.entry("gwalior", new GeoPoint(26.2183, 78.1828)),
            Map.entry("jhansi", new GeoPoint(25.4484, 78.5685)),
            Map.entry("sagar", new GeoPoint(23.8388, 78.7378)),
            Map.entry("bhopal", new GeoPoint(23.2599, 77.4126)),
            Map.entry("indore", new GeoPoint(22.7196, 75.8577)),
            Map.entry("dhule", new GeoPoint(20.9042, 74.7749)),
            Map.entry("aurangabad", new GeoPoint(19.8762, 75.3433)),
            Map.entry("amravati", new GeoPoint(20.9374, 77.7796)),
            Map.entry("nagpur", new GeoPoint(21.1458, 79.0882)),
            Map.entry("jabalpur", new GeoPoint(23.1815, 79.9864)),
            Map.entry("raipur", new GeoPoint(21.2514, 81.6296)),
            Map.entry("sambalpur", new GeoPoint(21.4669, 83.9812)),
            Map.entry("patna", new GeoPoint(25.5941, 85.1376)),
            Map.entry("ranchi", new GeoPoint(23.3441, 85.3096)),
            Map.entry("dhanbad", new GeoPoint(23.7957, 86.4304)),
            Map.entry("durgapur", new GeoPoint(23.5204, 87.3119)),
            Map.entry("kolkata", new GeoPoint(22.5726, 88.3639)),
            Map.entry("calcutta", new GeoPoint(22.5726, 88.3639)),
            Map.entry("berhampore", new GeoPoint(24.0988, 88.2679)),
            Map.entry("malda", new GeoPoint(25.0108, 88.1411)),
            Map.entry("raiganj", new GeoPoint(25.6185, 88.1256)),
            Map.entry("siliguri", new GeoPoint(26.7271, 88.3953)),
            Map.entry("alipurduar", new GeoPoint(26.4919, 89.5271)),
            Map.entry("kokrajhar", new GeoPoint(26.4011, 90.2725)),
            Map.entry("guwahati", new GeoPoint(26.1445, 91.7362)),
            Map.entry("shillong", new GeoPoint(25.5788, 91.8933)),
            Map.entry("nagaon", new GeoPoint(26.3464, 92.6840)),
            Map.entry("dimapur", new GeoPoint(25.9091, 93.7266)),
            Map.entry("kohima", new GeoPoint(25.6751, 94.1086)),
            Map.entry("imphal", new GeoPoint(24.8170, 93.9368)),
            Map.entry("haflong", new GeoPoint(25.1648, 93.0176)),
            Map.entry("silchar", new GeoPoint(24.8333, 92.7789)),
            Map.entry("aizawl", new GeoPoint(23.7271, 92.7176)),
            Map.entry("agartala", new GeoPoint(23.8315, 91.2868)),
            Map.entry("bhubaneswar", new GeoPoint(20.2961, 85.8245)),
            Map.entry("berhampur", new GeoPoint(19.3149, 84.7941)),
            Map.entry("visakhapatnam", new GeoPoint(17.6868, 83.2185)),
            Map.entry("rajahmundry", new GeoPoint(17.0005, 81.8040)),
            Map.entry("vijayawada", new GeoPoint(16.5062, 80.6480)),
            Map.entry("nellore", new GeoPoint(14.4426, 79.9865)),
            Map.entry("chennai", new GeoPoint(13.0827, 80.2707)),
            Map.entry("puducherry", new GeoPoint(11.9416, 79.8083)),
            Map.entry("pondicherry", new GeoPoint(11.9416, 79.8083)),
            Map.entry("tirupati", new GeoPoint(13.6288, 79.4192)),
            Map.entry("adilabad", new GeoPoint(19.6641, 78.5320)),
            Map.entry("nizamabad", new GeoPoint(18.6725, 78.0941)),
            Map.entry("hyderabad", new GeoPoint(17.3850, 78.4867)),
            Map.entry("kurnool", new GeoPoint(15.8281, 78.0373)),
            Map.entry("anantapur", new GeoPoint(14.6819, 77.6006)),
            Map.entry("bengaluru", new GeoPoint(12.9716, 77.5946)),
            Map.entry("bangalore", new GeoPoint(12.9716, 77.5946)),
            Map.entry("mysuru", new GeoPoint(12.2958, 76.6394)),
            Map.entry("mangalore", new GeoPoint(12.9141, 74.8560)),
            Map.entry("mangaluru", new GeoPoint(12.9141, 74.8560)),
            Map.entry("hubballi", new GeoPoint(15.3647, 75.1240)),
            Map.entry("belagavi", new GeoPoint(15.8497, 74.4977)),
            Map.entry("salem", new GeoPoint(11.6643, 78.1460)),
            Map.entry("coimbatore", new GeoPoint(11.0168, 76.9558)),
            Map.entry("kochi", new GeoPoint(9.9312, 76.2673)),
            Map.entry("cochin", new GeoPoint(9.9312, 76.2673)),
            Map.entry("thiruvananthapuram", new GeoPoint(8.5241, 76.9366)),
            Map.entry("trivandrum", new GeoPoint(8.5241, 76.9366)),
            Map.entry("madurai", new GeoPoint(9.9252, 78.1198)),
            Map.entry("kanyakumari", new GeoPoint(8.0883, 77.5385))
    );

    private final AutopilotTripRepository tripRepository;
    private final AutopilotStopRepository stopRepository;
    private final AutopilotActionRepository actionRepository;
    private final RouteExperienceRepository experienceRepository;
    private final VehicleRepository vehicleRepository;
    private final ChargingStationRepository stationRepository;
    private final BookingService bookingService;
    private final WalletService walletService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final RoutingService routingService;

    @Transactional(readOnly = true)
    public AutopilotPlanResponse previewTrip(Long userId, AutopilotTripRequest request) {
        Vehicle vehicle = ownedVehicle(request.getVehicleId(), userId);
        validateConstraints(request);

        GeoPoint origin = resolveLocation(request.getOrigin(), CITY_COORDINATES.get("kanpur"));
        GeoPoint destination = resolveLocation(request.getDestination(), CITY_COORDINATES.get("delhi"));
        double routeDistance = Math.max(20, haversine(origin, destination) * ROAD_DISTANCE_FACTOR);
        double capacityKwh = batteryCapacity(vehicle);
        String optimization = normalizedOptimization(request.getOptimizeFor());
        String autonomyMode = normalizedAutonomyMode(request.getAutonomyMode());
        TripPurpose purpose = resolvedPurpose(request);
        RouteMemory memory = routeMemory(request.getOrigin(), request.getDestination());
        List<Candidate> candidates = compatibleCandidates(
                vehicle, origin, destination, routeDistance, capacityKwh, optimization, purpose, memory);
        List<Candidate> selected = selectReachableStops(
                candidates,
                routeDistance,
                capacityKwh,
                request.getCurrentBatteryPercent(),
                request.getMinimumArrivalBatteryPercent(),
                purpose,
                optimization
        );

        AutopilotTrip proposal = AutopilotTrip.builder()
                .vehicleId(vehicle.getId())
                .origin(request.getOrigin().trim())
                .destination(request.getDestination().trim())
                .tripPurpose(purpose)
                .memorySummary(memory.summary())
                .startingBatteryPercent(round(request.getCurrentBatteryPercent()))
                .currentBatteryPercent(round(request.getCurrentBatteryPercent()))
                .minimumArrivalBatteryPercent(round(request.getMinimumArrivalBatteryPercent()))
                .maximumChargingBudget(roundMoney(request.getMaximumChargingBudget()))
                .totalDistanceKm(round(routeDistance))
                .estimatedArrivalBatteryPercent(round(request.getMinimumArrivalBatteryPercent() + 4))
                .build();
        List<AutopilotStop> stops = buildStops(proposal, selected, capacityKwh);
        double totalCost = stops.stream().mapToDouble(AutopilotStop::getEstimatedCost).sum();
        if (totalCost > request.getMaximumChargingBudget()) {
            throw new BadRequestException("No safe route fits the ₹" + roundMoney(request.getMaximumChargingBudget())
                    + " charging budget. Increase the budget to at least ₹" + Math.ceil(totalCost) + ".");
        }

        int driveMinutes = (int) Math.ceil(routeDistance / 68.0 * 60);
        int chargingMinutes = stops.stream()
                .mapToInt(stop -> stop.getChargingMinutes() + stop.getEstimatedWaitMinutes())
                .sum();
        int totalMinutes = driveMinutes + chargingMinutes;
        LocalTime departure = LocalTime.now();
        DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");
        int accumulatedStopMinutes = 0;
        List<AutopilotPlanStopResponse> plannedStops = new ArrayList<>();
        for (int index = 0; index < stops.size(); index++) {
            AutopilotStop stop = stops.get(index);
            Candidate candidate = selected.get(index);
            int minutesFromDeparture = (int) Math.ceil(stop.getDistanceFromOriginKm() / 68.0 * 60)
                    + accumulatedStopMinutes;
            long availableConnectors = candidate.station().getConnectors().stream()
                    .filter(ChargingConnector::isAvailable)
                    .filter(connector -> !connector.isMaintenanceMode())
                    .filter(connector -> connector.getStatus() == ChargerStatus.ONLINE)
                    .count();
            plannedStops.add(AutopilotPlanStopResponse.builder()
                    .sequenceNumber(stop.getSequenceNumber())
                    .stationId(stop.getStationId())
                    .stationName(stop.getStationName())
                    .stationAddress(stop.getStationAddress())
                    .connectorType(stop.getConnectorType())
                    .powerKw(stop.getPowerKw())
                    .distanceFromOriginKm(stop.getDistanceFromOriginKm())
                    .estimatedArrivalTime(departure.plusMinutes(minutesFromDeparture).format(clock))
                    .predictedSlotFreeAt(departure.plusMinutes(minutesFromDeparture
                            + stop.getEstimatedWaitMinutes()).format(clock))
                    .timingScore(timingScore(stop.getEstimatedWaitMinutes()))
                    .timingLabel(timingLabel(stop.getEstimatedWaitMinutes()))
                    .arrivalBatteryPercent(stop.getArrivalBatteryPercent())
                    .targetBatteryPercent(stop.getTargetBatteryPercent())
                    .estimatedWaitMinutes(stop.getEstimatedWaitMinutes())
                    .chargingMinutes(stop.getChargingMinutes())
                    .estimatedCost(stop.getEstimatedCost())
                    .availableConnectors((int) availableConnectors)
                    .queueCount(candidate.station().getQueueCount())
                    .rating(candidate.station().getRating())
                    .selectionReason(candidate.selectionReason())
                    .build());
            accumulatedStopMinutes += stop.getEstimatedWaitMinutes() + stop.getChargingMinutes();
        }

        double arrivalBattery = round(request.getMinimumArrivalBatteryPercent() + 4);
        return AutopilotPlanResponse.builder()
                .vehicleId(vehicle.getId())
                .vehicleName(vehicle.getMakeAndModel())
                .registrationNumber(vehicle.getRegistrationNumber())
                .connectorType(vehicle.getConnectorType())
                .origin(request.getOrigin().trim())
                .destination(request.getDestination().trim())
                .arrivalDeadline(blankToNull(request.getArrivalDeadline()))
                .estimatedArrivalTime(departure.plusMinutes(totalMinutes).format(clock))
                .optimizeFor(optimization)
                .tripPurpose(purpose.name())
                .purposeSummary(purposeSummary(purpose, request.getDestination()))
                .pastExperiencesUsed(memory.totalExperiences())
                .memorySummary(memory.summary())
                .autonomyMode(autonomyMode)
                .currentBatteryPercent(round(request.getCurrentBatteryPercent()))
                .minimumArrivalBatteryPercent(round(request.getMinimumArrivalBatteryPercent()))
                .maximumChargingBudget(roundMoney(request.getMaximumChargingBudget()))
                .totalDistanceKm(round(routeDistance))
                .estimatedDriveMinutes(driveMinutes)
                .totalDurationMinutes(totalMinutes)
                .estimatedChargingCost(roundMoney(totalCost))
                .budgetRemaining(roundMoney(request.getMaximumChargingBudget() - totalCost))
                .estimatedArrivalBatteryPercent(arrivalBattery)
                .compatibleChargersEvaluated(candidates.size())
                .withinBudget(totalCost <= request.getMaximumChargingBudget())
                .safeArrivalReserve(arrivalBattery >= request.getMinimumArrivalBatteryPercent())
                .liveAvailabilityChecked(true)
                .confirmationRequired(!"RECOMMEND_ONLY".equals(autonomyMode))
                .stops(plannedStops)
                .build();
    }

    @Transactional
    public AutopilotTripResponse launchTrip(Long userId, AutopilotTripRequest request) {
        String idempotencyKey = normalizedIdempotencyKey(request.getIdempotencyKey());
        var existing = tripRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Vehicle vehicle = ownedVehicle(request.getVehicleId(), userId);
        validateConstraints(request);
        String autonomyMode = normalizedAutonomyMode(request.getAutonomyMode());
        if ("RECOMMEND_ONLY".equals(autonomyMode)) {
            throw new BadRequestException("Recommend Only mode cannot create bookings. Choose Ask Before Actions or Full Autopilot to launch.");
        }

        GeoPoint origin = resolveLocation(request.getOrigin(), CITY_COORDINATES.get("kanpur"));
        GeoPoint destination = resolveLocation(request.getDestination(), CITY_COORDINATES.get("delhi"));
        double routeDistance = Math.max(20, haversine(origin, destination) * ROAD_DISTANCE_FACTOR);
        double capacityKwh = batteryCapacity(vehicle);
        String optimization = normalizedOptimization(request.getOptimizeFor());
        TripPurpose purpose = resolvedPurpose(request);
        RouteMemory memory = routeMemory(request.getOrigin(), request.getDestination());
        List<Candidate> candidates = compatibleCandidates(
                vehicle, origin, destination, routeDistance, capacityKwh, optimization, purpose, memory);
        List<Candidate> selected = selectReachableStops(
                candidates,
                routeDistance,
                capacityKwh,
                request.getCurrentBatteryPercent(),
                request.getMinimumArrivalBatteryPercent(),
                purpose,
                optimization
        );

        AutopilotTrip trip = tripRepository.save(AutopilotTrip.builder()
                .userId(userId)
                .vehicleId(vehicle.getId())
                .idempotencyKey(idempotencyKey)
                .goal(normalizedGoal(request))
                .origin(request.getOrigin().trim())
                .destination(request.getDestination().trim())
                .tripPurpose(purpose)
                .memorySummary(memory.summary())
                .arrivalDeadline(blankToNull(request.getArrivalDeadline()))
                .optimizeFor(optimization)
                .autonomyMode(autonomyMode)
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
        addAction(trip, AutopilotActionState.INFO, "Autonomy permissions set",
                "FULL_AUTOPILOT".equals(autonomyMode)
                        ? "Automatic booking and rerouting enabled inside the approved reserve and budget limits."
                        : "The approved trip may proceed; new consequential actions remain protected by user limits.");
        addAction(trip, AutopilotActionState.SUCCESS, "Route analyzed",
                candidates.size() + " compatible charging options scored by travel impact, queue, power and price.");
        addAction(trip, AutopilotActionState.INFO, "Journey purpose applied",
                purposeSummary(purpose, trip.getDestination()));
        if (memory.totalExperiences() > 0) {
            addAction(trip, AutopilotActionState.INFO, "Past route experience applied", memory.summary());
        }
        addAction(trip, AutopilotActionState.INFO, "Trip plan created",
                stops.size() + " safe charging stop" + (stops.size() == 1 ? "" : "s")
                        + " · estimated charging ₹" + roundMoney(totalCost) + ".");

        reserveAllStops(trip, stops, userId);
        notificationService.sendNotification(userId, "Vidyut Autopilot is ready",
                "All " + stops.size() + " timing-matched charging stop(s) are tentatively reserved for "
                        + trip.getDestination() + ".",
                NotificationType.BOOKING_CONFIRMED, "vidyut://autopilot?tripId=" + trip.getId());
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
        remember(trip, current.getStationId(), RouteExperienceOutcome.CHARGER_FAULT,
                current.getStationName() + " stopped responding and forced a reroute.", null, null);

        if (current.getBookingId() != null) {
            bookingService.cancelBookingWithoutFee(current.getBookingId(), userId,
                    "The charger became unavailable, so Vidyut released this reservation without a fee.");
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
                trip.getOptimizeFor(),
                trip.getTripPurpose(),
                routeMemory(trip.getOrigin(), trip.getDestination())
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
            remember(trip, activeStop.getStationId(), RouteExperienceOutcome.PAYMENT_ISSUE,
                    trip.getPaymentMessage(), null, null);
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
        AutopilotStop nextStop = stopRepository
                .findFirstByTripIdAndStatusOrderBySequenceNumberAsc(tripId, AutopilotStopStatus.RESERVED)
                .orElse(null);
        trip.setStatus(nextStop == null ? AutopilotTripStatus.COMPLETED : AutopilotTripStatus.MONITORING);
        trip.setActiveStationId(nextStop == null ? null : nextStop.getStationId());
        trip.setActiveBookingId(nextStop == null ? null : nextStop.getBookingId());
        trip.setPaymentMessage("Paid with Vidyut AutoPay · " + payment.getGatewayTransactionId());
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        addAction(trip, AutopilotActionState.SUCCESS, "Charging completed",
                round(activeStop.getTargetBatteryPercent()) + "% battery · connector released.");
        addAction(trip, AutopilotActionState.SUCCESS, "Wallet paid automatically",
                "₹" + roundMoney(activeStop.getEstimatedCost()) + " paid · " + payment.getGatewayTransactionId() + ".");
        addAction(trip, AutopilotActionState.SUCCESS,
                nextStop == null ? "Journey completed" : "Journey continues",
                nextStop == null ? "All timing-matched charging stops are complete."
                        : "Navigation resumed toward " + nextStop.getStationName() + ".");
        remember(trip, activeStop.getStationId(), RouteExperienceOutcome.SUCCESS,
                "Charging completed and AutoPay succeeded at " + activeStop.getStationName() + ".", 5, 0);
        notificationService.sendNotification(userId, "Charging and AutoPay complete",
                "₹" + roundMoney(activeStop.getEstimatedCost()) + " paid at " + activeStop.getStationName() + ".",
                NotificationType.CHARGING_COMPLETED);
        return toResponse(trip);
    }

    @Transactional
    public RouteExperienceResponse recordExperience(Long tripId, Long userId, RouteExperienceRequest input) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        if (input.stationId() != null) {
            boolean belongsToTrip = stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId).stream()
                    .anyMatch(stop -> stop.getStationId().equals(input.stationId()));
            if (!belongsToTrip) throw new BadRequestException("The selected station was not part of this trip");
        }
        RouteExperience saved = experienceRepository.save(RouteExperience.builder()
                .userId(userId).tripId(tripId).stationId(input.stationId())
                .origin(trip.getOrigin()).destination(trip.getDestination())
                .originKey(routeKey(trip.getOrigin())).destinationKey(routeKey(trip.getDestination()))
                .outcome(input.outcome()).detail(blankToNull(input.detail()))
                .rating(input.rating()).delayMinutes(input.delayMinutes()).build());
        addAction(trip, AutopilotActionState.INFO, "Route experience saved",
                "Future plans on this corridor will retrieve this "
                        + input.outcome().name().toLowerCase(Locale.ROOT) + " signal before selecting a stop.");
        return mapExperience(saved);
    }

    @Transactional(readOnly = true)
    public List<RouteStationResponse> stopAlternatives(Long tripId, Long stopId, Long userId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        AutopilotStop stop = ownedStop(tripId, stopId);
        return routingService.alternatives(userId, stop.getStationId(), trip.getVehicleId());
    }

    @Transactional
    public AutopilotTripResponse swapStop(Long tripId, Long stopId, Long alternativeStationId, Long userId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        AutopilotStop stop = ownedStop(tripId, stopId);
        RouteStationResponse alternative = routingService.alternatives(
                        userId, stop.getStationId(), trip.getVehicleId()).stream()
                .filter(candidate -> candidate.getStation().getId().equals(alternativeStationId))
                .findFirst().orElseThrow(() -> new BadRequestException(
                        "The selected station is not a compatible live alternative"));
        if (stop.getBookingId() != null) {
            bookingService.cancelBookingWithoutFee(stop.getBookingId(), userId,
                    "Autopilot stop swapped by the driver");
        }
        double oldCost = stop.getEstimatedCost();
        var station = alternative.getStation();
        var connector = station.getConnectors().stream()
                .filter(ChargingConnector::isAvailable)
                .max(Comparator.comparingDouble(ChargingConnector::getPowerKw))
                .orElseThrow(() -> new BadRequestException("Alternative has no available connector"));
        stop.setStationId(station.getId());
        stop.setStationName(station.getName());
        stop.setStationAddress(station.getAddress());
        stop.setConnectorType(connector.getType().name());
        stop.setPowerKw(connector.getPowerKw());
        stop.setDistanceFromOriginKm(alternative.getDistanceFromOriginKm());
        stop.setEstimatedWaitMinutes(Math.max(0, station.getQueueCount() * 7));
        stop.setChargingMinutes(alternative.getRecommendedChargeMinutes());
        stop.setEstimatedCost(alternative.getEstimatedChargingCost());
        stop.setSelectionReason(alternative.getReason());
        stop.setBookingId(null);
        stop.setStatus(AutopilotStopStatus.PLANNED);
        stopRepository.save(stop);
        reserveNextStop(trip, stop, userId);
        trip.setEstimatedChargingCost(roundMoney(
                Math.max(0, trip.getEstimatedChargingCost() - oldCost + stop.getEstimatedCost())));
        trip.setStatus(AutopilotTripStatus.REROUTED);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);
        addAction(trip, AutopilotActionState.SUCCESS, "Charging stop swapped",
                station.getName() + " is reserved. Downstream arrival estimates were recalculated.");
        notificationService.sendNotification(userId, "Trip plan updated",
                station.getName() + " replaced the previous charging stop.",
                NotificationType.AGENT_REPLAN, "vidyut://autopilot?tripId=" + tripId);
        return toResponse(trip);
    }

    @Transactional
    public AutopilotTripResponse simulateDelay(Long tripId, Long userId, int delayMinutes) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        addAction(trip, AutopilotActionState.WARNING, "Delay detected",
                "The trip is running " + delayMinutes + " minutes behind. Timing-matched stops are being checked.");
        notificationService.sendNotification(userId, "Vidyut is replanning",
                "You are running " + delayMinutes + " minutes behind. Tap to review the updated stop.",
                NotificationType.AGENT_REPLAN, "vidyut://autopilot?tripId=" + tripId);
        return simulateChargerFault(tripId, userId);
    }

    @Transactional(readOnly = true)
    public AutopilotTripSummaryResponse summary(Long tripId, Long userId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        List<AutopilotStop> stops = stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId);
        int chargingMinutes = stops.stream().filter(stop -> stop.getStatus() != AutopilotStopStatus.CANCELLED)
                .mapToInt(AutopilotStop::getChargingMinutes).sum();
        int stopCount = (int) stops.stream()
                .filter(stop -> stop.getStatus() != AutopilotStopStatus.CANCELLED).count();
        double co2 = round(trip.getTotalDistanceKm() * 0.12);
        String share = "Vidyut trip " + trip.getOrigin() + " → " + trip.getDestination() + ": "
                + trip.getTotalDistanceKm() + " km, " + stopCount + " charging stops, ₹"
                + trip.getEstimatedChargingCost() + ", " + co2 + " kg CO₂ saved.";
        return new AutopilotTripSummaryResponse(tripId, trip.getOrigin(), trip.getDestination(),
                trip.getTotalDistanceKm(), trip.getTotalDurationMinutes(), chargingMinutes,
                stopCount, trip.getEstimatedChargingCost(), co2, share);
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
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory
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
            double toDestination = haversine(stationPoint, destination) * ROAD_DISTANCE_FACTOR;
            double detour = Math.max(0,
                    (haversine(origin, stationPoint) + haversine(stationPoint, destination))
                            * ROAD_DISTANCE_FACTOR - routeDistance);
            int waitMinutes = Math.max(0, station.getQueueCount() * 7)
                    + (int) Math.round(station.getOccupancyPercent() / 20.0 * 3);
            int sampleChargeMinutes = Math.max(8,
                    (int) Math.ceil(capacityKwh * 0.35 / Math.max(7.4, connector.getPowerKw()) * 60) + 3);
            double reliabilityPenalty = Math.max(0, 5 - station.getRating()) * 8;
            MemorySignal signal = memory.stationSignals().getOrDefault(station.getId(), MemorySignal.EMPTY);
            double memoryPenalty = signal.failures() * 55 + signal.averageDelayMinutes() * 0.7
                    + signal.lowRatings() * 18 - signal.successes() * 4;
            String amenities = station.getAmenities() == null ? "" : station.getAmenities().toLowerCase(Locale.ROOT);
            boolean restFriendly = amenities.matches(".*(restroom|restaurant|food|cafe|lounge|hotel|washroom).*" );
            double purposePenalty = switch (purpose) {
                case MALL_VISIT, DESTINATION_CHARGING -> toDestination * 1.8;
                case REST_STOP -> restFriendly ? 0 : 90;
                case COMMUTE -> waitMinutes * 0.8;
                default -> 0;
            };
            double timeImpact = waitMinutes + sampleChargeMinutes + detour * 0.65 + reliabilityPenalty
                    + memoryPenalty + purposePenalty;
            double priceImpact = station.getPricePerKwh() * 2.2;
            double impact = switch (optimizeFor) {
                case "COST" -> timeImpact * 0.45 + priceImpact * 1.8;
                case "BALANCED" -> timeImpact * 0.8 + priceImpact;
                default -> timeImpact + priceImpact * 0.25;
            };
            String reason = selectionReason(purpose, station, round(toDestination), restFriendly, signal);
            Candidate candidate = new Candidate(station, connector, round(fromOrigin), round(toDestination), round(detour),
                    waitMinutes, impact, restFriendly, reason);
            allCompatible.add(candidate);
            double routeUpperBound = purpose == TripPurpose.MALL_VISIT || purpose == TripPurpose.DESTINATION_CHARGING
                    ? routeDistance + 20 : routeDistance - 5;
            if (fromOrigin > 10 && fromOrigin < routeUpperBound && detour <= Math.max(90, routeDistance * 0.22)) {
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
            double minimumBattery,
            TripPurpose purpose,
            String optimizeFor
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
            // Time-first routes stay close to the furthest safe progress point.
            // Cost-first routes may accept a shorter leg when a substantially
            // cheaper value charger is available, while still moving forward.
            double progressWindowKm = switch (optimizeFor) {
                case "COST" -> 105;
                case "BALANCED" -> 60;
                default -> 35;
            };
            Candidate chosen = reachable.stream()
                    .filter(candidate -> candidate.distanceFromOriginKm() >= furthestMarker - progressWindowKm)
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
            Comparator<Candidate> convenienceComparator = switch (purpose) {
                case MALL_VISIT, DESTINATION_CHARGING -> Comparator.comparingDouble(candidate ->
                        candidate.distanceToDestinationKm() * 2 + candidate.impactMinutes() * 0.15);
                case REST_STOP -> Comparator.comparingDouble(candidate ->
                        (candidate.restFriendly() ? 0 : 500)
                                + Math.abs(candidate.distanceFromOriginKm() - routeDistance * 0.5)
                                + candidate.impactMinutes() * 0.2);
                default -> Comparator.comparingDouble(candidate ->
                        Math.abs(candidate.distanceFromOriginKm() - routeDistance * 0.55)
                                + candidate.impactMinutes());
            };
            Candidate convenienceStop = candidates.stream().min(convenienceComparator).orElseThrow();
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
                    .selectionReason(candidate.selectionReason())
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
                .selectionReason(candidate.selectionReason())
                .status(AutopilotStopStatus.PLANNED)
                .build();
    }

    private void reserveNextStop(AutopilotTrip trip, AutopilotStop stop, Long userId) {
        reserveStop(trip, stop, userId, Math.max(15, trip.getEstimatedDriveMinutes() / 3), true);
    }

    private void reserveAllStops(AutopilotTrip trip, List<AutopilotStop> stops, Long userId) {
        int accumulatedStopMinutes = 0;
        for (int index = 0; index < stops.size(); index++) {
            AutopilotStop stop = stops.get(index);
            int arrivalMinutes = (int) Math.ceil(stop.getDistanceFromOriginKm() / 68.0 * 60)
                    + accumulatedStopMinutes;
            reserveStop(trip, stop, userId, Math.max(15, arrivalMinutes), index == 0);
            accumulatedStopMinutes += stop.getEstimatedWaitMinutes() + stop.getChargingMinutes();
        }
        addAction(trip, AutopilotActionState.SUCCESS, "All stops tentatively booked",
                stops.size() + " reservations were created in one confirmation.");
    }

    private void reserveStop(AutopilotTrip trip, AutopilotStop stop, Long userId,
                             int arrivalMinutes, boolean makeActive) {
        BookingResponse booking = bookingService.createBooking(BookingCreateRequest.builder()
                .stationId(stop.getStationId())
                .vehicleId(trip.getVehicleId())
                .startTime(LocalDateTime.now().plusMinutes(arrivalMinutes))
                .durationHours(Math.max(1, (int) Math.ceil(stop.getChargingMinutes() / 60.0)))
                .idempotencyKey("AUTOPILOT-" + trip.getId() + "-STOP-" + stop.getId())
                .build(), userId);
        stop.setBookingId(booking.getId());
        stop.setStatus(AutopilotStopStatus.RESERVED);
        stopRepository.save(stop);
        if (makeActive) {
            trip.setActiveStationId(stop.getStationId());
            trip.setActiveBookingId(booking.getId());
        }
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
                .tripPurpose(trip.getTripPurpose() == null ? TripPurpose.GENERAL.name() : trip.getTripPurpose().name())
                .memorySummary(trip.getMemorySummary())
                .autonomyMode(normalizedAutonomyMode(trip.getAutonomyMode()))
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
                .selectionReason(stop.getSelectionReason())
                .timingScore(timingScore(stop.getEstimatedWaitMinutes()))
                .timingLabel(timingLabel(stop.getEstimatedWaitMinutes()))
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

    private AutopilotStop ownedStop(Long tripId, Long stopId) {
        return stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId).stream()
                .filter(stop -> stop.getId().equals(stopId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Autopilot stop not found for this trip"));
    }

    private String timingScore(int waitMinutes) {
        if (waitMinutes <= 15) return "HIGH";
        if (waitMinutes <= 30) return "MEDIUM";
        return "LOW";
    }

    private String timingLabel(int waitMinutes) {
        if (waitMinutes <= 15) return "Arrives just in time";
        if (waitMinutes <= 30) return "Short wait · about " + waitMinutes + " min";
        return "Better option may be nearby";
    }

    private TripPurpose resolvedPurpose(AutopilotTripRequest request) {
        if (request.getTripPurpose() != null && !request.getTripPurpose().isBlank()) {
            try {
                return TripPurpose.valueOf(request.getTripPurpose().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall through to natural-language intent detection.
            }
        }
        String intent = ((request.getGoal() == null ? "" : request.getGoal()) + " "
                + (request.getDestination() == null ? "" : request.getDestination())).toLowerCase(Locale.ROOT);
        if (intent.matches(".*(mall|shopping|market|cinema).*")) return TripPurpose.MALL_VISIT;
        if (intent.matches(".*(rest|break|food|meal|cafe|hotel|washroom|sleep).*")) return TripPurpose.REST_STOP;
        if (intent.matches(".*(office|work|commute|college|school).*")) return TripPurpose.COMMUTE;
        if (intent.matches(".*(charge near|charger near|destination charg).*")) return TripPurpose.DESTINATION_CHARGING;
        return TripPurpose.GENERAL;
    }

    private String purposeSummary(TripPurpose purpose, String destination) {
        return switch (purpose) {
            case MALL_VISIT -> "Mall visit: prefer a compatible charger close to " + destination
                    + " so parking time also becomes charging time.";
            case REST_STOP -> "Rest stop: prefer an on-route charger with food, restroom, lounge or hotel amenities.";
            case COMMUTE -> "Commute: prioritize dependable low-wait chargers and minimize repeat delay.";
            case DESTINATION_CHARGING -> "Destination charging: keep the final charger close to " + destination + ".";
            case GENERAL -> "General journey: balance safe range, detour, live queue, charging speed and price.";
        };
    }

    private String selectionReason(TripPurpose purpose, ChargingStation station, double destinationDistance,
            boolean restFriendly, MemorySignal signal) {
        String base = switch (purpose) {
            case MALL_VISIT -> destinationDistance + " km from the destination, allowing charging during the mall visit";
            case REST_STOP -> restFriendly
                    ? "Rest-friendly amenities are available while the vehicle charges"
                    : "Best reachable stop after comparing route impact and live availability";
            case COMMUTE -> "Low queue and dependable charging reduce commute delay";
            case DESTINATION_CHARGING -> destinationDistance + " km from the destination with a compatible live connector";
            case GENERAL -> "Selected for safe reachability, total detour, queue, speed and price";
        };
        if (signal.successes() > 0) return base + "; " + signal.successes() + " successful past route experience(s) support it";
        if (signal.failures() > 0) return base + "; " + signal.failures() + " past issue penalty/penalties applied";
        return base + (station.getAmenities() == null ? "" : "; amenities: " + station.getAmenities());
    }

    private RouteMemory routeMemory(String origin, String destination) {
        List<RouteExperience> experiences = experienceRepository
                .findTop30ByOriginKeyAndDestinationKeyOrderByCreatedAtDesc(routeKey(origin), routeKey(destination));
        Map<Long, MutableMemorySignal> mutable = new HashMap<>();
        int successes = 0;
        int issues = 0;
        for (RouteExperience experience : experiences) {
            if (experience.getOutcome() == RouteExperienceOutcome.SUCCESS) successes++; else issues++;
            if (experience.getStationId() == null) continue;
            MutableMemorySignal signal = mutable.computeIfAbsent(experience.getStationId(), ignored -> new MutableMemorySignal());
            if (experience.getOutcome() == RouteExperienceOutcome.SUCCESS) signal.successes++;
            else signal.failures++;
            if (experience.getRating() != null && experience.getRating() <= 2) signal.lowRatings++;
            if (experience.getDelayMinutes() != null) {
                signal.delayTotal += experience.getDelayMinutes();
                signal.delaySamples++;
            }
        }
        Map<Long, MemorySignal> signals = new HashMap<>();
        mutable.forEach((stationId, signal) -> signals.put(stationId,
                new MemorySignal(signal.successes, signal.failures, signal.lowRatings,
                        signal.delaySamples == 0 ? 0 : signal.delayTotal / signal.delaySamples)));
        String summary = experiences.isEmpty()
                ? "No previous journey experience exists for this corridor yet."
                : "Retrieved " + experiences.size() + " prior route experience(s): " + successes
                        + " successful and " + issues + " issue signal(s). Problem stations receive a planning penalty.";
        return new RouteMemory(experiences.size(), summary, signals);
    }

    private void remember(AutopilotTrip trip, Long stationId, RouteExperienceOutcome outcome, String detail,
            Integer rating, Integer delayMinutes) {
        if (stationId != null
                && experienceRepository.existsByTripIdAndStationIdAndOutcome(trip.getId(), stationId, outcome)) return;
        experienceRepository.save(RouteExperience.builder()
                .userId(trip.getUserId()).tripId(trip.getId()).stationId(stationId)
                .origin(trip.getOrigin()).destination(trip.getDestination())
                .originKey(routeKey(trip.getOrigin())).destinationKey(routeKey(trip.getDestination()))
                .outcome(outcome).detail(detail).rating(rating).delayMinutes(delayMinutes).build());
    }

    private RouteExperienceResponse mapExperience(RouteExperience experience) {
        return new RouteExperienceResponse(experience.getId(), experience.getTripId(), experience.getStationId(),
                experience.getOrigin(), experience.getDestination(), experience.getOutcome(), experience.getDetail(),
                experience.getRating(), experience.getDelayMinutes(), experience.getCreatedAt());
    }

    private String routeKey(String value) {
        if (value == null) return "unknown";
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ")
                .replaceAll(" +", " ").trim();
        for (String city : CITY_COORDINATES.keySet()) if (normalized.contains(city)) return city;
        return normalized.isBlank() ? "unknown" : normalized.substring(0, Math.min(120, normalized.length()));
    }

    private String normalizedOptimization(String optimizeFor) {
        if (optimizeFor == null || optimizeFor.isBlank()) return "TIME";
        String normalized = optimizeFor.trim().toUpperCase(Locale.ROOT);
        return Set.of("TIME", "COST", "BALANCED").contains(normalized) ? normalized : "TIME";
    }

    private String normalizedAutonomyMode(String autonomyMode) {
        if (autonomyMode == null || autonomyMode.isBlank()) return "ASK_BEFORE_ACTIONS";
        String normalized = autonomyMode.trim().toUpperCase(Locale.ROOT);
        return Set.of("RECOMMEND_ONLY", "ASK_BEFORE_ACTIONS", "FULL_AUTOPILOT").contains(normalized)
                ? normalized
                : "ASK_BEFORE_ACTIONS";
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
            double distanceToDestinationKm,
            double detourKm,
            int waitMinutes,
            double impactMinutes,
            boolean restFriendly,
            String selectionReason
    ) {}

    private record MemorySignal(int successes, int failures, int lowRatings, int averageDelayMinutes) {
        private static final MemorySignal EMPTY = new MemorySignal(0, 0, 0, 0);
    }

    private record RouteMemory(int totalExperiences, String summary, Map<Long, MemorySignal> stationSignals) {}

    private static final class MutableMemorySignal {
        private int successes;
        private int failures;
        private int lowRatings;
        private int delayTotal;
        private int delaySamples;
    }
}
