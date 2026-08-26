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
import com.vidyut.autopilot.dto.VehicleRecommendationOptionResponse;
import com.vidyut.autopilot.dto.VehicleRecommendationRequest;
import com.vidyut.autopilot.dto.VehicleRecommendationResponse;
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
import com.vidyut.routing.client.OsrmClient;
import com.vidyut.routing.dto.Coordinate;
import com.vidyut.routing.dto.OsrmGeometry;
import com.vidyut.routing.dto.OsrmResponse;
import com.vidyut.routing.dto.OsrmRoute;
import com.vidyut.routing.dto.OsrmTableResponse;
import com.vidyut.routing.dto.RouteStationResponse;
import com.vidyut.routing.exception.OsrmException;
import com.vidyut.routing.service.LocationResolver;
import com.vidyut.routing.service.RouteCorridorService;
import com.vidyut.routing.service.RoutingService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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

    private static final double ENERGY_PER_KM_KWH = 0.14;
    private static final int MAX_STOPS = 20;

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
    private final OsrmClient osrmClient;
    private final LocationResolver locationResolver;
    private final RouteCorridorService routeCorridorService;
    private final ChargingRouteOptimizer chargingRouteOptimizer;
    private final VehicleChargingProfileService vehicleChargingProfileService;
    private final VehicleRecommendationRanker vehicleRecommendationRanker;
    private final DeadlineEvaluator deadlineEvaluator;

    @Value("${vidyut.routing.corridor-time-km:8}")
    private double timeCorridorKm;

    @Value("${vidyut.routing.corridor-balanced-km:12}")
    private double balancedCorridorKm;

    @Value("${vidyut.routing.corridor-cost-km:20}")
    private double costCorridorKm;

    @Value("${vidyut.routing.stop-endpoint-buffer-km:10}")
    private double stopEndpointBufferKm;

    @Value("${vidyut.routing.corridor-margin-degrees:2.5}")
    private double corridorMarginDegrees;

    @Value("${vidyut.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    @Value("${vidyut.autopilot.current-trip-max-age-hours:72}")
    private long currentTripMaxAgeHours;

    private OsrmRoute getOsrmRoute(Coordinate origin, Coordinate destination) {
        return getOsrmRoute(List.of(origin, destination));
    }

    private OsrmRoute getOsrmRoute(List<Coordinate> waypoints) {
        return getOsrmRoute(waypoints, OsrmClient.RouteEngine.PRIMARY);
    }

    private OsrmRoute getOsrmRoute(List<Coordinate> waypoints, OsrmClient.RouteEngine engine) {
        OsrmResponse response;
        try {
            response = osrmClient.getRoute(waypoints, engine);
        } catch (OsrmException exception) {
            if (exception.isLocationOutsideCoverage()) {
                throw new BadRequestException(
                        "The origin or destination is outside the configured OpenStreetMap coverage");
            }
            throw exception;
        }
        if (response == null || !"Ok".equals(response.code())
                || response.routes() == null || response.routes().isEmpty()) {
            throw new BadRequestException(
                    "No drivable route was found in the configured OpenStreetMap coverage");
        }
        return response.routes().get(0);
    }

    private BaseRoute getBestBaseRoute(Coordinate origin, Coordinate destination) {
        return getBestRoute(List.of(origin, destination));
    }

    private BaseRoute getBestRoute(List<Coordinate> waypoints) {
        OsrmClient.RouteSelection selection = osrmClient.getBestRoute(waypoints);
        OsrmResponse response = selection.response();
        if (response == null || !"Ok".equals(response.code())
                || response.routes() == null || response.routes().isEmpty()) {
            throw new BadRequestException("No drivable base route was found for this journey");
        }
        OsrmRoute route = response.routes().get(0);
        if (route.geometry() == null || route.geometry().coordinates() == null
                || route.geometry().coordinates().size() < 2) {
            throw new BadRequestException("The route engine did not return a usable base-route polyline");
        }
        if (route.legs() == null || route.legs().size() != waypoints.size() - 1) {
            throw new BadRequestException("The route engine did not return every journey leg");
        }
        return new BaseRoute(route, selection.engine());
    }

    @Transactional(readOnly = true)
    public VehicleRecommendationResponse recommendVehicle(
            Long userId,
            VehicleRecommendationRequest request
    ) {
        List<Vehicle> vehicles = vehicleRepository.findByUserId(userId);
        if (vehicles.isEmpty()) {
            throw new BadRequestException("Add at least one EV before asking Vidyut to choose a vehicle");
        }

        String optimization = normalizedOptimization(request.getOptimizeFor());
        List<VehicleEvaluation> evaluations = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            double currentBattery = vehicle.getBatteryPercent() != null
                    ? vehicle.getBatteryPercent()
                    : request.getFallbackBatteryPercent();
            if (currentBattery <= request.getMinimumArrivalBatteryPercent()) {
                evaluations.add(new VehicleEvaluation(vehicle, null,
                        recommendationOption(vehicle, currentBattery, null, false,
                                "Current battery is not above the requested "
                                        + round(request.getMinimumArrivalBatteryPercent()) + "% reserve.")));
                continue;
            }

            AutopilotTripRequest tripRequest = AutopilotTripRequest.builder()
                    .vehicleId(vehicle.getId())
                    .origin(request.getOrigin())
                    .destination(request.getDestination())
                    .goal(request.getGoal())
                    .tripPurpose(request.getTripPurpose())
                    .arrivalDeadline(request.getArrivalDeadline())
                    .optimizeFor(optimization)
                    .autonomyMode(request.getAutonomyMode())
                    .currentBatteryPercent(currentBattery)
                    .minimumArrivalBatteryPercent(request.getMinimumArrivalBatteryPercent())
                    .maximumChargingBudget(request.getMaximumChargingBudget())
                    .idempotencyKey("VEHICLE-COMPARISON-" + vehicle.getId() + "-" + UUID.randomUUID())
                    .build();
            try {
                AutopilotPlanResponse plan = previewTrip(userId, tripRequest);
                boolean feasible = plan.isOverallFeasible();
                evaluations.add(new VehicleEvaluation(vehicle, plan,
                        recommendationOption(vehicle, currentBattery, plan, feasible,
                                feasible ? feasibleVehicleReason(plan, optimization)
                                        : infeasiblePlanReason(plan))));
            } catch (BadRequestException planningFailure) {
                evaluations.add(new VehicleEvaluation(vehicle, null,
                        recommendationOption(vehicle, currentBattery, null, false,
                                planningFailureReason(vehicle, planningFailure))));
            }
        }

        Comparator<VehicleEvaluation> ranking = Comparator.comparing(
                VehicleEvaluation::option,
                vehicleRecommendationRanker.comparator(optimization));
        VehicleRecommendationOptionResponse recommendedOption = vehicleRecommendationRanker.recommended(
                evaluations.stream().map(VehicleEvaluation::option).toList(), optimization).orElse(null);
        VehicleEvaluation recommended = recommendedOption == null ? null : evaluations.stream()
                .filter(evaluation -> evaluation.option().getVehicleId().equals(recommendedOption.getVehicleId()))
                .findFirst()
                .orElse(null);

        if (recommended != null) {
            String reason = recommended.plan() != null && recommended.plan().isOverallFeasible()
                    ? recommendedVehicleReason(recommended.plan(), optimization)
                    : (recommended.plan() != null
                            ? "Best match: " + infeasiblePlanReason(recommended.plan())
                            : recommended.option().getReason());
            recommended.option().setReason(reason);
        }
        List<VehicleRecommendationOptionResponse> options = evaluations.stream()
                .sorted((first, second) -> {
                    if (first == recommended) return -1;
                    if (second == recommended) return 1;
                    if (first.option().isFeasible() != second.option().isFeasible()) {
                        return first.option().isFeasible() ? -1 : 1;
                    }
                    return first.option().isFeasible()
                            ? ranking.compare(first, second)
                            : first.vehicle().getMakeAndModel().compareToIgnoreCase(second.vehicle().getMakeAndModel());
                })
                .map(VehicleEvaluation::option)
                .toList();

        String reason = recommended == null
                ? "None of the saved vehicles satisfies connector reachability, battery reserve, budget and deadline together."
                : recommended.option().getReason();
        return VehicleRecommendationResponse.builder()
                .recommendedVehicleId(recommended == null ? null : recommended.vehicle().getId())
                .recommendedVehicleName(recommended == null ? null : recommended.vehicle().getMakeAndModel())
                .reason(reason)
                .origin(request.getOrigin().trim())
                .destination(request.getDestination().trim())
                .optimizeFor(optimization)
                .recommendedPlan(recommended == null ? null : recommended.plan())
                .vehicles(options)
                .build();
    }

    @Transactional(readOnly = true)
    public AutopilotPlanResponse previewTrip(Long userId, AutopilotTripRequest request) {
        Vehicle vehicle = ownedVehicle(request.getVehicleId(), userId);
        validateConstraints(request);
        LocalTime departure = LocalTime.now().withSecond(0).withNano(0);
        DeadlineEvaluator.DeadlineAssessment deadlineWindow = deadlineEvaluator.assess(
                request.getArrivalDeadline(), departure, 0);
        VehicleChargingProfileService.ChargingProfile chargingProfile =
                vehicleChargingProfileService.forVehicle(vehicle);
        Coordinate origin = locationResolver.resolve(request.getOrigin());
        Coordinate destination = locationResolver.resolve(request.getDestination());
        double capacityKwh = batteryCapacity(vehicle);
        String optimization = normalizedOptimization(request.getOptimizeFor());
        String autonomyMode = normalizedAutonomyMode(request.getAutonomyMode());
        TripPurpose purpose = resolvedPurpose(request);
        RouteMemory memory = routeMemory(request.getOrigin(), request.getDestination());
        PlanningContext plan = planJourney(
                vehicle, request, origin, destination, capacityKwh,
                optimization, purpose, memory, chargingProfile,
                deadlineWindow.availableMinutes());

        double baseDistanceKm = round(plan.baseRoute().route().distance() / 1000.0);
        double totalDistanceKm = round(plan.finalRoute().distance() / 1000.0);
        double detourDistanceKm = round(Math.max(0, totalDistanceKm - baseDistanceKm));
        int baseDriveMinutes = (int) Math.ceil(plan.baseRoute().route().duration() / 60.0);
        int driveMinutes = (int) Math.ceil(plan.finalRoute().duration() / 60.0);
        int detourMinutes = Math.max(0, driveMinutes - baseDriveMinutes);
        ChargingRouteOptimizer.OptimizationResult optimized = plan.optimized();
        int totalMinutes = driveMinutes + optimized.chargingMinutes()
                + optimized.queueMinutes() + optimized.connectionMinutes();
        DeadlineEvaluator.DeadlineAssessment deadlineAssessment = deadlineEvaluator.assess(
                request.getArrivalDeadline(), departure, totalMinutes);
        boolean withinBudget = optimized.cost() <= request.getMaximumChargingBudget();
        boolean safeArrivalReserve = optimized.arrivalBatteryPercent()
                >= request.getMinimumArrivalBatteryPercent();
        boolean overallFeasible = withinBudget && safeArrivalReserve && deadlineAssessment.feasible();
        String summary = optimizationSummary(
                optimization, optimized, plan.candidates().size(), corridorLimitKm(optimization), plan);
        if (!deadlineAssessment.feasible()) {
            summary += " The battery-safe preview misses the requested arrival deadline by "
                    + deadlineAssessment.minutesLate() + " minutes.";
        }

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
                .totalDistanceKm(totalDistanceKm)
                .baseRouteDistanceKm(baseDistanceKm)
                .chargingDetourDistanceKm(detourDistanceKm)
                .estimatedDriveMinutes(driveMinutes)
                .baseDriveMinutes(baseDriveMinutes)
                .chargingDetourMinutes(detourMinutes)
                .estimatedChargingMinutes(optimized.chargingMinutes())
                .estimatedQueueMinutes(optimized.queueMinutes())
                .connectionOverheadMinutes(optimized.connectionMinutes())
                .totalDurationMinutes(totalMinutes)
                .estimatedChargingCost(optimized.cost())
                .estimatedArrivalBatteryPercent(optimized.arrivalBatteryPercent())
                .feasibleAlternativesCompared(optimized.feasibleAlternatives())
                .optimizationSummary(summary)
                .routeEngine(routeEngineLabel(plan))
                .build();
        List<AutopilotStop> stops = buildOptimizedStops(proposal, plan);
        DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");
        int accumulatedStopMinutes = 0;
        double cumulativeDriveSeconds = 0;
        List<AutopilotPlanStopResponse> plannedStops = new ArrayList<>();
        for (int index = 0; index < stops.size(); index++) {
            AutopilotStop stop = stops.get(index);
            Candidate candidate = plan.selected().get(index);
            cumulativeDriveSeconds += plan.finalRoute().legs().get(index).duration();
            int arrivalOffset = (int) Math.ceil(cumulativeDriveSeconds / 60.0) + accumulatedStopMinutes;
            int stopMinutes = stop.getChargingMinutes() + stop.getEstimatedWaitMinutes()
                    + stop.getConnectionMinutes();
            int departOffset = arrivalOffset + stopMinutes;
            plannedStops.add(AutopilotPlanStopResponse.builder()
                    .sequenceNumber(stop.getSequenceNumber())
                    .stationId(stop.getStationId())
                    .stationName(stop.getStationName())
                    .stationAddress(stop.getStationAddress())
                    .connectorType(stop.getConnectorType())
                    .powerKw(stop.getPowerKw())
                    .effectivePowerKw(stop.getEffectivePowerKw())
                    .distanceFromOriginKm(stop.getDistanceFromOriginKm())
                    .routeOffsetKm(stop.getRouteOffsetKm())
                    .estimatedArrivalTime(departure.plusMinutes(arrivalOffset).format(clock))
                    .predictedSlotFreeAt(departure.plusMinutes(departOffset).format(clock))
                    .timingScore(timingScore(stop.getEstimatedWaitMinutes()))
                    .timingLabel(timingLabel(stop.getEstimatedWaitMinutes()))
                    .arrivalBatteryPercent(stop.getArrivalBatteryPercent())
                    .targetBatteryPercent(stop.getTargetBatteryPercent())
                    .estimatedWaitMinutes(stop.getEstimatedWaitMinutes())
                    .chargingMinutes(stop.getChargingMinutes())
                    .connectionMinutes(stop.getConnectionMinutes())
                    .estimatedCost(stop.getEstimatedCost())
                    .demoData(stop.isDemoData())
                    .availableConnectors((int) candidate.station().getConnectors().stream().filter(ChargingConnector::isAvailable).count())
                    .queueCount(candidate.station().getQueueCount())
                    .rating(candidate.station().getRating())
                    .selectionReason(stop.getSelectionReason())
                    .build());
            accumulatedStopMinutes += stopMinutes;
        }

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
                .purposeSummary(purposeSummary(purpose, request.getDestination().trim()))
                .pastExperiencesUsed(memory.totalExperiences())
                .memorySummary(memory.summary())
                .autonomyMode(autonomyMode)
                .currentBatteryPercent(round(request.getCurrentBatteryPercent()))
                .minimumArrivalBatteryPercent(round(request.getMinimumArrivalBatteryPercent()))
                .maximumChargingBudget(roundMoney(request.getMaximumChargingBudget()))
                .totalDistanceKm(totalDistanceKm)
                .baseRouteDistanceKm(baseDistanceKm)
                .chargingDetourDistanceKm(detourDistanceKm)
                .estimatedDriveMinutes(driveMinutes)
                .baseDriveMinutes(baseDriveMinutes)
                .chargingDetourMinutes(detourMinutes)
                .estimatedChargingMinutes(optimized.chargingMinutes())
                .estimatedQueueMinutes(optimized.queueMinutes())
                .connectionOverheadMinutes(optimized.connectionMinutes())
                .totalDurationMinutes(totalMinutes)
                .estimatedChargingCost(optimized.cost())
                .budgetRemaining(roundMoney(request.getMaximumChargingBudget() - optimized.cost()))
                .estimatedArrivalBatteryPercent(optimized.arrivalBatteryPercent())
                .batteryCapacityKwh(round(capacityKwh))
                .availableEnergyKwh(round(capacityKwh
                        * (request.getCurrentBatteryPercent() - request.getMinimumArrivalBatteryPercent()) / 100.0))
                .energyConsumptionKwhPer100Km(round(vehicleEnergyPerKmKwh(vehicle) * 100))
                .vehicleMaxChargingPowerKw(round(chargingProfile.maximumDcPowerKw()))
                .chargingEfficiencyPercent(round(chargingProfile.efficiency() * 100))
                .compatibleChargersEvaluated(plan.candidates().size())
                .feasibleAlternativesCompared(optimized.feasibleAlternatives())
                .optimizationSummary(summary)
                .routeEngine(routeEngineLabel(plan))
                .withinBudget(withinBudget)
                .safeArrivalReserve(safeArrivalReserve)
                .deadlineFeasible(deadlineAssessment.feasible())
                .overallFeasible(overallFeasible)
                .deadlineMinutesLate(deadlineAssessment.minutesLate())
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
        LocalTime departure = LocalTime.now().withSecond(0).withNano(0);
        DeadlineEvaluator.DeadlineAssessment deadlineWindow = deadlineEvaluator.assess(
                request.getArrivalDeadline(), departure, 0);
        VehicleChargingProfileService.ChargingProfile chargingProfile =
                vehicleChargingProfileService.forVehicle(vehicle);
        String autonomyMode = normalizedAutonomyMode(request.getAutonomyMode());
        if ("RECOMMEND_ONLY".equals(autonomyMode)) {
            throw new BadRequestException("Recommend Only mode cannot create bookings. Choose Ask Before Actions or Full Autopilot to launch.");
        }

        Coordinate origin = locationResolver.resolve(request.getOrigin());
        Coordinate destination = locationResolver.resolve(request.getDestination());
        double capacityKwh = batteryCapacity(vehicle);
        String optimization = normalizedOptimization(request.getOptimizeFor());
        TripPurpose purpose = resolvedPurpose(request);
        RouteMemory memory = routeMemory(request.getOrigin(), request.getDestination());
        PlanningContext plan = planJourney(
                vehicle, request, origin, destination, capacityKwh,
                optimization, purpose, memory, chargingProfile,
                deadlineWindow.availableMinutes());
        ChargingRouteOptimizer.OptimizationResult optimized = plan.optimized();
        double baseDistanceKm = round(plan.baseRoute().route().distance() / 1000.0);
        double totalDistanceKm = round(plan.finalRoute().distance() / 1000.0);
        double detourDistanceKm = round(Math.max(0, totalDistanceKm - baseDistanceKm));
        int baseDriveMinutes = (int) Math.ceil(plan.baseRoute().route().duration() / 60.0);
        int driveMinutes = (int) Math.ceil(plan.finalRoute().duration() / 60.0);
        int detourMinutes = Math.max(0, driveMinutes - baseDriveMinutes);
        int totalMinutes = driveMinutes + optimized.chargingMinutes()
                + optimized.queueMinutes() + optimized.connectionMinutes();
        DeadlineEvaluator.DeadlineAssessment deadlineAssessment = deadlineEvaluator.assess(
                request.getArrivalDeadline(), departure, totalMinutes);
        String summary = optimizationSummary(
                optimization, optimized, plan.candidates().size(), corridorLimitKm(optimization), plan);

        double totalCost = optimized.cost();
        if (totalCost > request.getMaximumChargingBudget()) {
            throw new BadRequestException("No safe route fits the ₹" + roundMoney(request.getMaximumChargingBudget())
                    + " charging budget. Increase the budget to at least ₹" + Math.ceil(totalCost) + ".");
        }
        if (optimized.arrivalBatteryPercent() < request.getMinimumArrivalBatteryPercent()) {
            throw new BadRequestException("No route can protect the requested arrival battery reserve");
        }
        if (!deadlineAssessment.feasible()) {
            DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");
            throw new BadRequestException("This plan arrives at "
                    + deadlineAssessment.estimatedArrivalTime().format(clock)
                    + ", after the requested "
                    + deadlineAssessment.requestedArrivalTime().format(clock)
                    + " deadline by " + deadlineAssessment.minutesLate()
                    + " minutes. Change the deadline before launching Autopilot.");
        }

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
                .totalDistanceKm(totalDistanceKm)
                .baseRouteDistanceKm(baseDistanceKm)
                .chargingDetourDistanceKm(detourDistanceKm)
                .estimatedDriveMinutes(driveMinutes)
                .baseDriveMinutes(baseDriveMinutes)
                .chargingDetourMinutes(detourMinutes)
                .estimatedChargingMinutes(optimized.chargingMinutes())
                .estimatedQueueMinutes(optimized.queueMinutes())
                .connectionOverheadMinutes(optimized.connectionMinutes())
                .totalDurationMinutes(totalMinutes)
                .estimatedChargingCost(optimized.cost())
                .estimatedArrivalBatteryPercent(optimized.arrivalBatteryPercent())
                .feasibleAlternativesCompared(optimized.feasibleAlternatives())
                .optimizationSummary(summary)
                .routeEngine(routeEngineLabel(plan))
                .status(AutopilotTripStatus.RESERVED)
                .build());

        List<AutopilotStop> stops = buildOptimizedStops(trip, plan);
        stopRepository.saveAll(stops);

        int chargingMinutes = optimized.chargingMinutes() + optimized.queueMinutes()
                + optimized.connectionMinutes();
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
                plan.candidates().size() + " chargers inside the " + corridorLimitKm(optimization)
                        + " km base-route corridor were scored; "
                        + optimized.feasibleAlternatives() + " feasible energy states reached the destination.");
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
        return tripRepository.findFirstByUserIdAndStatusInAndUpdatedAtAfterOrderByCreatedAtDesc(userId, List.of(
                        AutopilotTripStatus.RESERVED,
                        AutopilotTripStatus.MONITORING,
                        AutopilotTripStatus.REROUTED,
                        AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED,
                        AutopilotTripStatus.REPLAN_REQUIRED,
                        AutopilotTripStatus.PAYMENT_REQUIRED
                ), LocalDateTime.now().minusHours(Math.max(1, currentTripMaxAgeHours)))
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

        boolean executeAutomatically = automaticFaultRecoveryAllowed(trip.getAutonomyMode());
        return recoverUnavailableStop(trip, current,
                "The charger stopped responding.", executeAutomatically);
    }

    static boolean automaticFaultRecoveryAllowed(String autonomyMode) {
        return autonomyMode != null
                && "FULL_AUTOPILOT".equals(autonomyMode.trim().toUpperCase(Locale.ROOT));
    }

    @Transactional
    public AutopilotTripResponse recordOperationalPropagation(Long tripId, Long userId,
            String incidentCode, Long maintenanceTicketId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        String maintenance = maintenanceTicketId == null
                ? "operator follow-up requested"
                : "Company maintenance ticket #" + maintenanceTicketId;
        addAction(trip, AutopilotActionState.INFO, "Operations incident propagated",
                incidentCode + " · " + maintenance
                        + " · Host and Company notified · Admin audit recorded.");
        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> connectorDisruptionImpact(
            Long stationId,
            String connectorType,
            Long excludedConnectorId
    ) {
        boolean backupAvailable = stationRepository.findById(stationId)
                .map(station -> station.getConnectors().stream().anyMatch(connector ->
                        !connector.getId().equals(excludedConnectorId)
                                && connector.getType().name().equalsIgnoreCase(connectorType)
                                && connector.isAvailable()
                                && !connector.isMaintenanceMode()
                                && connector.getStatus() == ChargerStatus.ONLINE))
                .orElse(false);
        List<AutopilotStop> affectedStops = backupAvailable ? List.of()
                : stopRepository.findByStationIdAndStatus(stationId, AutopilotStopStatus.RESERVED).stream()
                        .filter(stop -> stop.getConnectorType().equalsIgnoreCase(connectorType))
                        .filter(stop -> tripRepository.findById(stop.getTripId())
                                .map(trip -> activeTripStatuses().contains(trip.getStatus()))
                                .orElse(false))
                        .toList();
        long fullAutopilot = affectedStops.stream().filter(stop -> tripRepository.findById(stop.getTripId())
                .map(trip -> "FULL_AUTOPILOT".equals(normalizedAutonomyMode(trip.getAutonomyMode())))
                .orElse(false)).map(AutopilotStop::getTripId).distinct().count();
        long approvalRequired = affectedStops.stream().map(AutopilotStop::getTripId).distinct().count()
                - fullAutopilot;
        return Map.of(
                "activeJourneys", affectedStops.stream().map(AutopilotStop::getTripId).distinct().count(),
                "automaticReroutes", fullAutopilot,
                "driverApprovals", approvalRequired,
                "backupConnectorAvailable", backupAvailable
        );
    }

    @Transactional
    public Map<String, Object> handleConnectorUnavailable(
            Long stationId,
            String connectorType,
            Long excludedConnectorId,
            String reason
    ) {
        Map<String, Object> impact = connectorDisruptionImpact(stationId, connectorType, excludedConnectorId);
        if (Boolean.TRUE.equals(impact.get("backupConnectorAvailable"))) {
            return Map.of("affectedJourneys", 0, "automaticReroutes", 0, "driverApprovals", 0,
                    "replanRequired", 0, "backupConnectorAvailable", true);
        }

        int automatic = 0;
        int approvals = 0;
        int replanRequired = 0;
        Set<Long> processedTrips = new HashSet<>();
        for (AutopilotStop stop : stopRepository
                .findByStationIdAndStatus(stationId, AutopilotStopStatus.RESERVED)) {
            if (!stop.getConnectorType().equalsIgnoreCase(connectorType)
                    || !processedTrips.add(stop.getTripId())) continue;
            AutopilotTrip trip = tripRepository.findById(stop.getTripId()).orElse(null);
            if (trip == null || !activeTripStatuses().contains(trip.getStatus())) continue;
            boolean automaticAction = "FULL_AUTOPILOT".equals(
                    normalizedAutonomyMode(trip.getAutonomyMode()));
            AutopilotTripResponse result = recoverUnavailableStop(trip, stop, reason, automaticAction);
            if (result.getStatus() == AutopilotTripStatus.REROUTED) automatic++;
            else if (result.getStatus() == AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED) approvals++;
            else if (result.getStatus() == AutopilotTripStatus.REPLAN_REQUIRED) replanRequired++;
        }
        return Map.of(
                "affectedJourneys", automatic + approvals + replanRequired,
                "automaticReroutes", automatic,
                "driverApprovals", approvals,
                "replanRequired", replanRequired,
                "backupConnectorAvailable", false
        );
    }

    @Transactional
    public AutopilotTripResponse approvePreparedReroute(Long tripId, Long userId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        if (trip.getStatus() != AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED) {
            throw new BadRequestException("This journey has no replacement charger awaiting approval");
        }
        AutopilotStop replacement = stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId).stream()
                .filter(stop -> stop.getStatus() == AutopilotStopStatus.PLANNED)
                .max(Comparator.comparingInt(AutopilotStop::getSequenceNumber))
                .orElseThrow(() -> new BadRequestException("No safe replacement charger is available to approve"));
        reserveNextStop(trip, replacement, userId);
        trip.setStatus(AutopilotTripStatus.REROUTED);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);
        addAction(trip, AutopilotActionState.SUCCESS, "Driver approved reroute",
                replacement.getStationName() + " is reserved and navigation is updated.");
        notificationService.sendNotification(userId, "Replacement charger reserved",
                replacement.getStationName() + " is now part of your active journey.",
                NotificationType.AGENT_REPLAN, "vidyut://autopilot?tripId=" + tripId);
        return toResponse(trip);
    }

    private AutopilotTripResponse recoverUnavailableStop(
            AutopilotTrip trip,
            AutopilotStop current,
            String reason,
            boolean executeAutomatically
    ) {
        Long userId = trip.getUserId();
        Long tripId = trip.getId();

        addAction(trip, AutopilotActionState.WARNING, "Station fault detected",
                current.getStationName() + " is unavailable. " + reason + " Full remaining-journey re-optimization initiated.");
        remember(trip, current.getStationId(), RouteExperienceOutcome.CHARGER_FAULT,
                current.getStationName() + " became unavailable and forced a full journey re-optimization.", null, null);

        // 1. Cancel the booking for the failed stop without fee
        if (current.getBookingId() != null) {
            bookingService.cancelBookingWithoutFee(current.getBookingId(), userId,
                    "The charger became unavailable, so Vidyut released this reservation without a fee.");
        }
        current.setStatus(AutopilotStopStatus.CANCELLED);
        current.setRemovalReason("CHARGER_FAULT");
        current.setOriginalStopIndex(current.getSequenceNumber());
        stopRepository.save(current);
        addAction(trip, AutopilotActionState.SUCCESS, "Old reservation released",
                "Booking #" + current.getBookingId() + " cancelled without fee.");

        // 2. Fetch all stops for this trip
        List<AutopilotStop> allStops = stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId);
        List<AutopilotStop> completedStops = allStops.stream()
                .filter(s -> s.getStatus() == AutopilotStopStatus.COMPLETED)
                .toList();
        List<AutopilotStop> staleUncompletedStops = allStops.stream()
                .filter(s -> (s.getStatus() == AutopilotStopStatus.PLANNED || s.getStatus() == AutopilotStopStatus.RESERVED)
                        && !s.getId().equals(current.getId()))
                .toList();

        // Release any pending bookings on stale downstream stops
        for (AutopilotStop stale : staleUncompletedStops) {
            if (stale.getBookingId() != null) {
                try {
                    bookingService.cancelBookingWithoutFee(stale.getBookingId(), userId,
                            "Journey rerouted following charger outage");
                } catch (Exception ignored) {}
            }
        }
        // Remove stale uncompleted stops so they can be replaced by the newly optimized stops
        if (!staleUncompletedStops.isEmpty()) {
            stopRepository.deleteAll(staleUncompletedStops);
        }

        // 3. Determine current vehicle state
        Vehicle vehicle = ownedVehicle(trip.getVehicleId(), userId);
        double capacityKwh = batteryCapacity(vehicle);
        VehicleChargingProfileService.ChargingProfile chargingProfile =
                vehicleChargingProfileService.forVehicle(vehicle);
        RouteMemory memory = routeMemory(trip.getOrigin(), trip.getDestination());

        Coordinate currentStartCoord;
        double currentBattery = trip.getCurrentBatteryPercent();
        double completedCost = completedStops.stream().mapToDouble(AutopilotStop::getEstimatedCost).sum();
        double completedDistanceKm = completedStops.isEmpty() ? 0 : completedStops.get(completedStops.size() - 1).getDistanceFromOriginKm();
        int completedDurationMinutes = completedStops.stream()
                .mapToInt(s -> s.getChargingMinutes() + s.getEstimatedWaitMinutes() + s.getConnectionMinutes())
                .sum();
        int completedChargingMinutes = completedStops.stream().mapToInt(AutopilotStop::getChargingMinutes).sum();
        int completedQueueMinutes = completedStops.stream().mapToInt(AutopilotStop::getEstimatedWaitMinutes).sum();
        int completedConnectionMinutes = completedStops.stream().mapToInt(AutopilotStop::getConnectionMinutes).sum();

        if (!completedStops.isEmpty()) {
            AutopilotStop lastCompleted = completedStops.get(completedStops.size() - 1);
            ChargingStation lastStation = stationRepository.findById(lastCompleted.getStationId()).orElse(null);
            if (lastStation != null) {
                currentStartCoord = new Coordinate(lastStation.getLatitude(), lastStation.getLongitude());
            } else {
                currentStartCoord = locationResolver.resolve(lastCompleted.getStationAddress());
            }
        } else {
            currentStartCoord = locationResolver.resolve(trip.getOrigin());
        }
        Coordinate destinationCoord = locationResolver.resolve(trip.getDestination());

        // 4. Set excluded stations: the failed station + all completed stations
        Set<Long> excludedStationIds = new HashSet<>();
        excludedStationIds.add(current.getStationId());
        completedStops.forEach(s -> excludedStationIds.add(s.getStationId()));

        double remainingBudget = Math.max(100.0, trip.getMaximumChargingBudget() - completedCost);

        AutopilotTripRequest replanRequest = AutopilotTripRequest.builder()
                .vehicleId(trip.getVehicleId())
                .origin(trip.getOrigin())
                .destination(trip.getDestination())
                .goal(trip.getGoal())
                .tripPurpose(trip.getTripPurpose() == null ? null : trip.getTripPurpose().name())
                .arrivalDeadline(trip.getArrivalDeadline())
                .optimizeFor(trip.getOptimizeFor())
                .autonomyMode(trip.getAutonomyMode())
                .currentBatteryPercent(currentBattery)
                .minimumArrivalBatteryPercent(trip.getMinimumArrivalBatteryPercent())
                .maximumChargingBudget(remainingBudget)
                .build();

        PlanningContext replanContext = null;
        try {
            replanContext = planJourney(
                    vehicle,
                    replanRequest,
                    currentStartCoord,
                    destinationCoord,
                    capacityKwh,
                    trip.getOptimizeFor(),
                    trip.getTripPurpose(),
                    memory,
                    chargingProfile,
                    null,
                    excludedStationIds
            );
        } catch (Exception e) {
            // Re-optimization failed
        }

        if (replanContext == null || replanContext.selected().isEmpty()) {
            trip.setStatus(AutopilotTripStatus.REPLAN_REQUIRED);
            trip.setActiveStationId(null);
            trip.setActiveBookingId(null);
            trip.setUpdatedAt(LocalDateTime.now());
            tripRepository.save(trip);
            addAction(trip, AutopilotActionState.WARNING, "No safe replacement available",
                    "The unavailable booking was released. Stop safely and review the journey before continuing.");
            notificationService.sendNotification(userId, "Journey needs a safe stop",
                    current.getStationName() + " is unavailable and no compatible replacement currently satisfies your limits.",
                    NotificationType.AGENT_REPLAN, "vidyut://autopilot?tripId=" + trip.getId());
            return toResponse(trip);
        }

        // 5. Build the newly optimized stops from replanContext
        List<Candidate> newSelected = replanContext.selected();
        ChargingRouteOptimizer.OptimizationResult newOptimized = replanContext.optimized();
        List<AutopilotStop> newlyBuiltStops = new ArrayList<>();

        double cumulativeDistanceKm = completedDistanceKm;
        int nextSequence = completedStops.size() + 1;

        double oldTotalDistance = trip.getTotalDistanceKm();
        double oldTotalCost = trip.getEstimatedChargingCost();
        int oldTotalDuration = trip.getTotalDurationMinutes();

        for (int i = 0; i < newSelected.size(); i++) {
            Candidate candidate = newSelected.get(i);
            ChargingRouteOptimizer.StopDecision decision = newOptimized.stops().get(i);
            cumulativeDistanceKm += replanContext.finalRoute().legs().get(i).distance() / 1000.0;

            AutopilotStop newStop = AutopilotStop.builder()
                    .tripId(tripId)
                    .sequenceNumber(nextSequence++)
                    .stationId(candidate.station().getId())
                    .stationName(candidate.station().getName())
                    .stationAddress(candidate.station().getAddress())
                    .connectorType(candidate.connector().getType().name())
                    .powerKw(round(candidate.connector().getPowerKw()))
                    .effectivePowerKw(decision.effectivePowerKw())
                    .distanceFromOriginKm(round(cumulativeDistanceKm))
                    .routeOffsetKm(candidate.routeOffsetKm())
                    .arrivalBatteryPercent(decision.arrivalBatteryPercent())
                    .targetBatteryPercent(decision.targetBatteryPercent())
                    .estimatedWaitMinutes(decision.queueMinutes())
                    .chargingMinutes(decision.chargingMinutes())
                    .connectionMinutes(decision.connectionMinutes())
                    .estimatedCost(decision.cost())
                    .demoData(candidate.station().isDemoData())
                    .selectionReason(candidate.selectionReason())
                    .status(AutopilotStopStatus.PLANNED)
                    .build();

            if (i == 0) {
                // First new stop is the direct replacement for the failed stop
                newStop.setSelectionType("REROUTED_REPLACEMENT");
                newStop.setReplacesStationId(current.getStationId());
                newStop.setReplacesStationName(current.getStationName());
                newStop.setRerouteReason("CHARGER_FAULT");
                newStop.setOriginalStopIndex(current.getSequenceNumber());
            }

            newlyBuiltStops.add(newStop);
        }

        // Calculate deltas from new optimization result
        double newTotalDistance = round(completedDistanceKm + replanContext.finalRoute().distance() / 1000.0);
        double newTotalCost = roundMoney(completedCost + newOptimized.cost());
        int newDriveMinutes = (int) Math.ceil(replanContext.finalRoute().duration() / 60.0);
        int newTotalDuration = completedDurationMinutes + newDriveMinutes + newOptimized.chargingMinutes()
                + newOptimized.queueMinutes() + newOptimized.connectionMinutes();

        AutopilotStop firstNewStop = newlyBuiltStops.get(0);
        firstNewStop.setAdditionalDistanceKm(round(Math.abs(newTotalDistance - oldTotalDistance)));
        firstNewStop.setAdditionalMinutes(Math.max(1, newTotalDuration - oldTotalDuration));
        firstNewStop.setAdditionalCost(roundMoney(Math.abs(newTotalCost - oldTotalCost)));

        // Update relationship links on current (failed stop)
        current.setReplacedByStationId(firstNewStop.getStationId());
        current.setReplacedByStationName(firstNewStop.getStationName());
        stopRepository.save(current);

        // Save all newly optimized stops
        List<AutopilotStop> savedNewStops = stopRepository.saveAll(newlyBuiltStops);
        firstNewStop = savedNewStops.get(0);

        // 6. Invalidate and recompute all cached trip metrics
        trip.setTotalDistanceKm(newTotalDistance);
        trip.setTotalDurationMinutes(newTotalDuration);
        trip.setEstimatedDriveMinutes(newDriveMinutes);
        trip.setEstimatedChargingMinutes(completedChargingMinutes + newOptimized.chargingMinutes());
        trip.setEstimatedQueueMinutes(completedQueueMinutes + newOptimized.queueMinutes());
        trip.setConnectionOverheadMinutes(completedConnectionMinutes + newOptimized.connectionMinutes());
        trip.setEstimatedChargingCost(newTotalCost);
        trip.setEstimatedArrivalBatteryPercent(round(newOptimized.arrivalBatteryPercent()));
        trip.setChargingDetourDistanceKm(round(Math.max(0, newTotalDistance - trip.getBaseRouteDistanceKm())));
        trip.setChargingDetourMinutes((int) Math.round(trip.getChargingDetourDistanceKm() / 70.0 * 60.0));
        trip.setFeasibleAlternativesCompared(newOptimized.feasibleAlternatives());
        trip.setOptimizationSummary(optimizationSummary(trip.getOptimizeFor(), newOptimized, replanContext.candidates().size(), corridorLimitKm(trip.getOptimizeFor()), replanContext));
        trip.setRouteEngine(routeEngineLabel(replanContext));

        if (executeAutomatically) {
            reserveNextStop(trip, firstNewStop, userId);
        }

        trip.setStatus(executeAutomatically
                ? AutopilotTripStatus.REROUTED
                : AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED);
        if (!executeAutomatically) {
            trip.setActiveStationId(firstNewStop.getStationId());
            trip.setActiveBookingId(null);
        }
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        // 7. Actions & Notifications
        addAction(trip, AutopilotActionState.SUCCESS, "Remaining journey re-optimized",
                "Recalculated full route to " + trip.getDestination() + " via " + firstNewStop.getStationName()
                        + " (arrival " + firstNewStop.getArrivalBatteryPercent() + "%, target " + firstNewStop.getTargetBatteryPercent() + "%).");

        if (executeAutomatically) {
            addAction(trip, AutopilotActionState.SUCCESS, "Booking transferred",
                    "Connector reserved under booking #" + firstNewStop.getBookingId() + " at " + firstNewStop.getStationName() + ".");
            addAction(trip, AutopilotActionState.SUCCESS, "Route updated",
                    "New total distance " + newTotalDistance + " km · charging estimate ₹" + newTotalCost
                            + " · arrival reserve " + round(newOptimized.arrivalBatteryPercent()) + "%.");
            notificationService.sendNotification(userId, "Route automatically updated",
                    "Your charger became unavailable. " + firstNewStop.getStationName()
                            + " is reserved and remaining journey is re-optimized.", NotificationType.FAULT_ALERT);
        } else {
            addAction(trip, AutopilotActionState.INFO, "Execution permission required",
                    "Review the re-optimized remaining journey before Vidyut creates its reservation.");
            notificationService.sendNotification(userId, "Approve a replacement charger",
                    firstNewStop.getStationName() + " is the re-optimized replacement. Open the journey to approve it.",
                    NotificationType.AGENT_REPLAN, "vidyut://autopilot?tripId=" + trip.getId());
        }

        return toResponse(trip);
    }

    private Set<AutopilotTripStatus> activeTripStatuses() {
        return Set.of(
                AutopilotTripStatus.RESERVED,
                AutopilotTripStatus.MONITORING,
                AutopilotTripStatus.REROUTED,
                AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED,
                AutopilotTripStatus.REPLAN_REQUIRED,
                AutopilotTripStatus.PAYMENT_REQUIRED
        );
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
        if (nextStop == null) {
            nextStop = stopRepository
                    .findFirstByTripIdAndStatusOrderBySequenceNumberAsc(tripId, AutopilotStopStatus.PLANNED)
                    .orElse(null);
            if (nextStop != null) {
                // Trips created by older versions reserved only the first stop. Promote the
                // next planned stop instead of incorrectly completing the whole journey.
                reserveNextStop(trip, nextStop, userId);
            }
        }
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

    private PlanningContext planJourney(
            Vehicle vehicle,
            AutopilotTripRequest request,
            Coordinate origin,
            Coordinate destination,
            double capacityKwh,
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory,
            VehicleChargingProfileService.ChargingProfile chargingProfile,
            Integer maximumTotalMinutes
    ) {
        return planJourney(vehicle, request, origin, destination, capacityKwh, optimizeFor, purpose, memory, chargingProfile, maximumTotalMinutes, Collections.emptySet());
    }

    private PlanningContext planJourney(
            Vehicle vehicle,
            AutopilotTripRequest request,
            Coordinate origin,
            Coordinate destination,
            double capacityKwh,
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory,
            VehicleChargingProfileService.ChargingProfile chargingProfile,
            Integer maximumTotalMinutes,
            Set<Long> excludedStationIds
    ) {
        BaseRoute baseRoute = getBestBaseRoute(origin, destination);
        List<Candidate> candidates = routeCandidates(
                vehicle, baseRoute.route(), optimizeFor, purpose, memory, excludedStationIds);
        List<Coordinate> matrixCoordinates = new ArrayList<>();
        matrixCoordinates.add(origin);
        candidates.stream().map(this::stationCoordinate).forEach(matrixCoordinates::add);
        matrixCoordinates.add(destination);

        OsrmClient.MatrixSelection matrixSelection =
                osrmClient.getBestFullTable(matrixCoordinates, baseRoute.engine());
        OsrmTableResponse matrix = matrixSelection.response();

        List<ChargingRouteOptimizer.ChargingOption> options = candidates.stream()
                .map(candidate -> new ChargingRouteOptimizer.ChargingOption(
                        candidate.station().getId(),
                        candidate.distanceFromOriginKm(),
                        candidate.routeOffsetKm(),
                        candidate.connector().getPowerKw(),
                        candidate.station().getPricePerKwh(),
                        candidate.waitMinutes(),
                        candidate.station().getRating()
                ))
                .toList();
        double energyPerKmKwh = vehicleEnergyPerKmKwh(vehicle);
        ChargingRouteOptimizer.OptimizationRequest optimizationRequest =
                new ChargingRouteOptimizer.OptimizationRequest(
                        options,
                        matrix,
                        capacityKwh,
                        energyPerKmKwh,
                        request.getCurrentBatteryPercent(),
                        request.getMinimumArrivalBatteryPercent(),
                        request.getMaximumChargingBudget(),
                        optimizeFor,
                        chargingProfile.maximumDcPowerKw(),
                        chargingProfile.efficiency(),
                        chargingProfile.curve(),
                        maximumTotalMinutes
                );

        ChargingRouteOptimizer.OptimizationResult optimized;
        try {
            optimized = chargingRouteOptimizer.optimize(optimizationRequest);
        } catch (BadRequestException budgetOrFeasibilityFailure) {
            try {
                optimized = chargingRouteOptimizer.optimize(
                        new ChargingRouteOptimizer.OptimizationRequest(
                                options,
                                matrix,
                                capacityKwh,
                                energyPerKmKwh,
                                request.getCurrentBatteryPercent(),
                                request.getMinimumArrivalBatteryPercent(),
                                Double.MAX_VALUE,
                                optimizeFor,
                                chargingProfile.maximumDcPowerKw(),
                                chargingProfile.efficiency(),
                                chargingProfile.curve(),
                                maximumTotalMinutes
                        ));
            } catch (BadRequestException deadlineOrEnergyFailure) {
                optimized = chargingRouteOptimizer.optimize(
                        new ChargingRouteOptimizer.OptimizationRequest(
                                options,
                                matrix,
                                capacityKwh,
                                energyPerKmKwh,
                                request.getCurrentBatteryPercent(),
                                request.getMinimumArrivalBatteryPercent(),
                                Double.MAX_VALUE,
                                optimizeFor,
                                chargingProfile.maximumDcPowerKw(),
                                chargingProfile.efficiency(),
                                chargingProfile.curve(),
                                null
                        ));
            }
        }

        Map<Long, Candidate> candidateByStation = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(
                        candidate -> candidate.station().getId(),
                        candidate -> candidate));
        List<Candidate> selected = optimized.stops().stream()
                .map(stop -> candidateByStation.get(stop.option().stationId()))
                .toList();
        if (selected.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BadRequestException("The optimizer returned an unknown charging station");
        }

        List<Coordinate> waypoints = new ArrayList<>();
        waypoints.add(origin);
        selected.stream().map(this::stationCoordinate).forEach(waypoints::add);
        waypoints.add(destination);
        BaseRoute finalRouteSelection = getBestRoute(waypoints);
        OsrmRoute finalRoute = finalRouteSelection.route();
        if (finalRoute.legs() == null || finalRoute.legs().size() != selected.size() + 1) {
            throw new BadRequestException("The route engine did not return every optimized journey leg");
        }

        return new PlanningContext(
                baseRoute,
                candidates,
                selected,
                optimized,
                finalRoute,
                finalRouteSelection.engine(),
                matrixSelection);
    }

    private List<Candidate> routeCandidates(
            Vehicle vehicle,
            OsrmRoute baseRoute,
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory
    ) {
        return routeCandidates(vehicle, baseRoute, optimizeFor, purpose, memory, Collections.emptySet());
    }

    private List<Candidate> routeCandidates(
            Vehicle vehicle,
            OsrmRoute baseRoute,
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory,
            Set<Long> excludedStationIds
    ) {
        boolean isReplan = excludedStationIds != null && !excludedStationIds.isEmpty();
        double corridorKm = isReplan ? Math.max(corridorLimitKm(optimizeFor) * 2.5, 45.0) : corridorLimitKm(optimizeFor);
        double baseDistanceKm = baseRoute.distance() / 1000.0;
        List<Candidate> candidates = evaluateCandidates(
                vehicle, baseRoute, corridorKm, baseDistanceKm, optimizeFor, purpose, memory, excludedStationIds);
        if (candidates.size() < 4) {
            double expandedCorridorKm = Math.max(corridorKm * 2.2, 55.0);
            candidates = evaluateCandidates(
                    vehicle, baseRoute, expandedCorridorKm, baseDistanceKm, optimizeFor, purpose, memory, excludedStationIds);
        }
        return candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::distanceFromOriginKm))
                .limit(60)
                .toList();
    }

    private List<Candidate> evaluateCandidates(
            Vehicle vehicle,
            OsrmRoute baseRoute,
            double corridorKm,
            double baseDistanceKm,
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory,
            Set<Long> excludedStationIds
    ) {
        RouteBounds routeBounds = routeBounds(baseRoute.geometry(), corridorKm);
        List<Candidate> candidates = new ArrayList<>();

        for (ChargingStation station : stationRepository.findPublishedStations(demoDataEnabled)) {
            if (excludedStationIds != null && excludedStationIds.contains(station.getId())) {
                continue;
            }
            if (station.getStatus() != StationStatus.ACTIVE
                    || station.getAvailability() == StationAvailability.UNAVAILABLE
                    || station.isEmergencyDisabled()) {
                continue;
            }
            if (!routeBounds.contains(station.getLatitude(), station.getLongitude())) {
                continue;
            }
            ChargingConnector connector = bestConnector(station, vehicle);
            if (connector == null) {
                continue;
            }

            RouteCorridorService.RouteMatch routeMatch;
            try {
                routeMatch = routeCorridorService.match(
                        new Coordinate(station.getLatitude(), station.getLongitude()),
                        baseRoute.geometry());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            boolean isReplan = excludedStationIds != null && !excludedStationIds.isEmpty();
            double endpointBufferKm = isReplan ? 2.0 : Math.min(Math.max(0, stopEndpointBufferKm), Math.max(3.0, baseDistanceKm * 0.04));
            if (routeMatch.offsetKm() > corridorKm
                    || routeMatch.progressKm() <= endpointBufferKm
                    || routeMatch.progressKm() >= baseDistanceKm - endpointBufferKm) {
                continue;
            }

            int waitMinutes = Math.max(0, station.getQueueCount() * 7)
                    + (int) Math.round(station.getOccupancyPercent() / 20.0 * 3);
            MemorySignal signal = memory.stationSignals().getOrDefault(station.getId(), MemorySignal.EMPTY);
            String amenities = station.getAmenities() == null ? "" : station.getAmenities().toLowerCase(Locale.ROOT);
            boolean restFriendly = amenities.matches(".*(restroom|restaurant|food|cafe|lounge|hotel|washroom).*");
            double reliabilityPenalty = Math.max(0, 5 - station.getRating()) * 8
                    + signal.failures() * 55 + signal.averageDelayMinutes() * 0.7;
            double impact = waitMinutes + reliabilityPenalty
                    + station.getPricePerKwh() * ("COST".equals(optimizeFor) ? 2 : 0.25);
            String reason = round(routeMatch.offsetKm()) + " km from the base route; "
                    + station.getAvailableSlots() + " live compatible connector(s)";
            if (purpose == TripPurpose.REST_STOP && restFriendly) {
                reason += "; rest amenities available";
            }
            candidates.add(new Candidate(
                    station,
                    connector,
                    round(routeMatch.progressKm()),
                    round(Math.max(0, baseDistanceKm - routeMatch.progressKm())),
                    round(routeMatch.offsetKm() * 2),
                    round(routeMatch.offsetKm()),
                    waitMinutes,
                    impact,
                    restFriendly,
                    reason
            ));
        }

        return candidates;
    }

    private RouteBounds routeBounds(OsrmGeometry geometry, double corridorKm) {
        if (geometry == null || geometry.coordinates() == null || geometry.coordinates().isEmpty()) {
            return RouteBounds.INDIA;
        }
        double minimumLatitude = Double.POSITIVE_INFINITY;
        double maximumLatitude = Double.NEGATIVE_INFINITY;
        double minimumLongitude = Double.POSITIVE_INFINITY;
        double maximumLongitude = Double.NEGATIVE_INFINITY;
        for (List<Double> coordinate : geometry.coordinates()) {
            if (coordinate == null || coordinate.size() < 2) continue;
            minimumLongitude = Math.min(minimumLongitude, coordinate.get(0));
            maximumLongitude = Math.max(maximumLongitude, coordinate.get(0));
            minimumLatitude = Math.min(minimumLatitude, coordinate.get(1));
            maximumLatitude = Math.max(maximumLatitude, coordinate.get(1));
        }
        if (!Double.isFinite(minimumLatitude) || !Double.isFinite(minimumLongitude)) {
            return RouteBounds.INDIA;
        }
        // Coarse rejection only: the exact polyline distance check below still owns
        // corridor eligibility. The generous conversion protects curved routes.
        double marginDegrees = Math.max(0.2, corridorKm / 80.0);
        return new RouteBounds(
                minimumLatitude - marginDegrees,
                maximumLatitude + marginDegrees,
                minimumLongitude - marginDegrees,
                maximumLongitude + marginDegrees);
    }

    private List<AutopilotStop> buildOptimizedStops(AutopilotTrip trip, PlanningContext plan) {
        List<AutopilotStop> stops = new ArrayList<>();
        double cumulativeDistanceKm = 0;
        for (int index = 0; index < plan.selected().size(); index++) {
            Candidate candidate = plan.selected().get(index);
            ChargingRouteOptimizer.StopDecision decision = plan.optimized().stops().get(index);
            cumulativeDistanceKm += plan.finalRoute().legs().get(index).distance() / 1000.0;
            stops.add(AutopilotStop.builder()
                    .tripId(trip.getId())
                    .sequenceNumber(index + 1)
                    .stationId(candidate.station().getId())
                    .stationName(candidate.station().getName())
                    .stationAddress(candidate.station().getAddress())
                    .connectorType(candidate.connector().getType().name())
                    .powerKw(round(candidate.connector().getPowerKw()))
                    .effectivePowerKw(decision.effectivePowerKw())
                    .distanceFromOriginKm(round(cumulativeDistanceKm))
                    .routeOffsetKm(candidate.routeOffsetKm())
                    .arrivalBatteryPercent(decision.arrivalBatteryPercent())
                    .targetBatteryPercent(decision.targetBatteryPercent())
                    .estimatedWaitMinutes(decision.queueMinutes())
                    .chargingMinutes(decision.chargingMinutes())
                    .connectionMinutes(decision.connectionMinutes())
                    .estimatedCost(decision.cost())
                    .demoData(candidate.station().isDemoData())
                    .selectionReason(candidate.selectionReason())
                    .status(AutopilotStopStatus.PLANNED)
                    .build());
        }
        return stops;
    }

    private double corridorLimitKm(String optimizeFor) {
        return switch (normalizedOptimization(optimizeFor)) {
            case "COST" -> Math.max(25, costCorridorKm);
            case "BALANCED" -> Math.max(18, balancedCorridorKm);
            default -> Math.max(14, timeCorridorKm);
        };
    }

    private String optimizationSummary(
            String optimizeFor,
            ChargingRouteOptimizer.OptimizationResult optimized,
            int candidateCount,
            double corridorKm,
            PlanningContext plan
    ) {
        String objective = switch (normalizedOptimization(optimizeFor)) {
            case "COST" -> "lowest-cost";
            case "BALANCED" -> "best time-cost-reliability";
            default -> "fastest";
        };
        String summary = "Selected the " + objective + " battery-safe plan after evaluating "
                + optimized.transitionsEvaluated() + " energy transitions across "
                + candidateCount + " chargers within " + round(corridorKm)
                + " km of the base route. Stops are retained only when they improve the objective "
                + "or are required to protect the battery reserve.";
        if (plan.baseRoute().engine() == OsrmClient.RouteEngine.ESTIMATED) {
            return summary + " Live road routing was unavailable, so distance and timing use a "
                    + "conservative estimated-road fallback.";
        }
        if (plan.matrixSelection().estimatedCells()
                || plan.finalRouteEngine() == OsrmClient.RouteEngine.ESTIMATED) {
            return summary + " Some charger-leg road data was unavailable and was replaced with "
                    + "conservative estimates; confirm navigation before departure.";
        }
        return summary;
    }

    private String routeEngineLabel(PlanningContext plan) {
        String base = switch (plan.baseRoute().engine()) {
            case GOOGLE -> "GOOGLE_ROUTES_TRAFFIC_AWARE";
            case REFERENCE -> "OSRM_FULL_MAP_REFERENCE";
            case ESTIMATED -> "ESTIMATED_ROAD_FALLBACK";
            default -> "OSRM_LOCAL_OPENSTREETMAP";
        };
        if (plan.baseRoute().engine() == OsrmClient.RouteEngine.ESTIMATED) {
            return base;
        }
        if (plan.matrixSelection().estimatedCells()
                || plan.finalRouteEngine() == OsrmClient.RouteEngine.ESTIMATED) {
            return base + "_WITH_ESTIMATED_CHARGER_LEGS";
        }
        return base;
    }

    private boolean needsCharging(
            double routeDistance,
            double capacityKwh,
            double currentBatteryPercent,
            double minimumBatteryPercent
    ) {
        double safeRange = capacityKwh * Math.max(0, currentBatteryPercent - minimumBatteryPercent)
                / 100.0 / ENERGY_PER_KM_KWH;
        return routeDistance > safeRange;
    }

    private boolean purposeRequiresStop(TripPurpose purpose) {
        return purpose == TripPurpose.MALL_VISIT
                || purpose == TripPurpose.REST_STOP
                || purpose == TripPurpose.DESTINATION_CHARGING;
    }

    private List<Candidate> compatibleCandidates(
            Vehicle vehicle,
            Coordinate origin,
            Coordinate destination,
            double routeDistance,
            double capacityKwh,
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory
    ) {
        List<Candidate> onRoute = new ArrayList<>();
        List<Candidate> allCompatible = new ArrayList<>();
        List<ChargingStation> allStations = stationRepository.findPublishedStations(demoDataEnabled);

        List<ChargingStation> validStations = allStations.stream()
                .filter(station -> station.getStatus() == StationStatus.ACTIVE
                        && station.getAvailability() != StationAvailability.UNAVAILABLE
                        && !station.isEmergencyDisabled())
                .filter(station -> withinCorridor(station, origin, destination))
                .filter(station -> bestConnector(station, vehicle.getConnectorType()) != null)
                .toList();

        if (validStations.isEmpty()) {
            throw new BadRequestException("No online charger matches " + vehicle.getConnectorType() + " for this route");
        }

        // Use real OSRM road matrix table if available
        List<Coordinate> stationCoords = validStations.stream()
                .map(s -> new Coordinate(s.getLatitude(), s.getLongitude()))
                .toList();

        Map<Integer, double[]> roadMetrics = new HashMap<>();
        for (OsrmClient.MatrixBatch batch : osrmClient.getBestMatrixTables(
                origin, stationCoords, destination, OsrmClient.RouteEngine.PRIMARY)) {
            OsrmTableResponse table = batch.response();
            if (table == null || !"Ok".equals(table.code())
                    || table.distances() == null || table.distances().isEmpty()) {
                throw new BadRequestException("The routing engine could not evaluate charging stops for this route");
            }
            int batchStationCount = batch.stationCoordinates().size();
            for (int localIndex = 0; localIndex < batchStationCount; localIndex++) {
                Double fromOriginM = matrixValue(table.distances(), 0, localIndex);
                Double toDestinationM = matrixValue(
                        table.distances(), localIndex + 1, batchStationCount);
                if (fromOriginM != null && toDestinationM != null) {
                    roadMetrics.put(
                            batch.stationIndexes().get(localIndex),
                            new double[]{fromOriginM / 1000.0, toDestinationM / 1000.0}
                    );
                }
            }
        }

        for (int i = 0; i < validStations.size(); i++) {
            ChargingStation station = validStations.get(i);
            ChargingConnector connector = bestConnector(station, vehicle.getConnectorType());
            double[] metric = roadMetrics.get(i);
            if (metric == null) {
                continue;
            }
            double fromOrigin = round(metric[0]);
            double toDestination = round(metric[1]);
            double detour = round(Math.max(0, fromOrigin + toDestination - routeDistance));

            int waitMinutes = Math.max(0, station.getQueueCount() * 7)
                    + (int) Math.round(station.getOccupancyPercent() / 20.0 * 3);
            int sampleChargeMinutes = Math.max(8,
                    (int) Math.ceil(capacityKwh * 0.35 / Math.max(7.4, connector.getPowerKw()) * 60) + 3);
            double reliabilityPenalty = Math.max(0, 5 - station.getRating()) * 8;
            MemorySignal signal = memory.stationSignals().getOrDefault(station.getId(), MemorySignal.EMPTY);
            double memoryPenalty = signal.failures() * 55 + signal.averageDelayMinutes() * 0.7
                    + signal.lowRatings() * 18 - signal.successes() * 4;
            String amenities = station.getAmenities() == null ? "" : station.getAmenities().toLowerCase(Locale.ROOT);
            boolean restFriendly = amenities.matches(".*(restroom|restaurant|food|cafe|lounge|hotel|washroom).*");
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
                    round(detour / 2.0), waitMinutes, impact, restFriendly, reason);
            allCompatible.add(candidate);
            double routeUpperBound = purpose == TripPurpose.MALL_VISIT || purpose == TripPurpose.DESTINATION_CHARGING
                    ? routeDistance + 20 : routeDistance - 5;
            if (fromOrigin > 10 && fromOrigin < routeUpperBound && detour <= Math.max(90, routeDistance * 0.22)) {
                onRoute.add(candidate);
            }
        }

        List<Candidate> result = onRoute.isEmpty() ? allCompatible : onRoute;
        if (result.isEmpty()) {
            throw new BadRequestException(
                    "No online " + vehicle.getConnectorType()
                            + " charger is routable inside the configured map coverage");
        }
        return result.stream()
                .sorted(Comparator.comparingDouble(Candidate::distanceFromOriginKm))
                .toList();
    }

    private boolean withinCorridor(
            ChargingStation station,
            Coordinate origin,
            Coordinate destination
    ) {
        double margin = Math.max(0, corridorMarginDegrees);
        return station.getLatitude() >= Math.min(origin.latitude(), destination.latitude()) - margin
                && station.getLatitude() <= Math.max(origin.latitude(), destination.latitude()) + margin
                && station.getLongitude() >= Math.min(origin.longitude(), destination.longitude()) - margin
                && station.getLongitude() <= Math.max(origin.longitude(), destination.longitude()) + margin;
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

            double maximumMarker = marker + safeRange * 0.95;
            double minimumMarker = marker + Math.min(15, safeRange * 0.15);
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
        if (selected.isEmpty() && purposeRequiresStop(purpose)) {
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

    private Double matrixValue(List<List<Double>> matrix, int row, int column) {
        if (matrix == null || row < 0 || row >= matrix.size()) {
            return null;
        }
        List<Double> values = matrix.get(row);
        return values == null || column < 0 || column >= values.size() ? null : values.get(column);
    }

    private Itinerary safeItinerary(
            Coordinate origin,
            List<Candidate> candidates,
            List<Candidate> initiallySelected,
            Coordinate destination,
            OsrmRoute directRoute,
            double capacityKwh,
            double startingBattery,
            double minimumBattery
    ) {
        List<Candidate> selected = new ArrayList<>(initiallySelected);
        double firstLegRangeKm = capacityKwh * Math.max(0, startingBattery - minimumBattery)
                / 100.0 / ENERGY_PER_KM_KWH;
        double chargedLegRangeKm = capacityKwh * Math.max(0, 80 - minimumBattery)
                / 100.0 / ENERGY_PER_KM_KWH;
        double directDistanceKm = directRoute.distance() / 1000.0;

        while (true) {
            Itinerary itinerary = itinerary(origin, selected, destination, directRoute);
            int unsafeLeg = -1;
            for (int legIndex = 0; legIndex < itinerary.legDistancesKm().size(); legIndex++) {
                double availableRangeKm = legIndex == 0 ? firstLegRangeKm : chargedLegRangeKm;
                if (itinerary.legDistancesKm().get(legIndex) > availableRangeKm) {
                    unsafeLeg = legIndex;
                    break;
                }
            }
            if (unsafeLeg < 0) {
                return itinerary;
            }
            if (selected.size() >= MAX_STOPS) {
                throw new BadRequestException("A safe route needs more than " + MAX_STOPS + " charging stops");
            }

            Candidate bridge = bridgeCandidate(
                    origin, destination, candidates, selected, unsafeLeg,
                    unsafeLeg == 0 ? firstLegRangeKm : chargedLegRangeKm,
                    directDistanceKm
            );
            if (bridge == null) {
                throw new BadRequestException(
                        "No compatible charger can safely bridge a "
                                + round(itinerary.legDistancesKm().get(unsafeLeg)) + " km route leg");
            }
            selected.add(unsafeLeg, bridge);
        }
    }

    private Candidate bridgeCandidate(
            Coordinate origin,
            Coordinate destination,
            List<Candidate> candidates,
            List<Candidate> selected,
            int legIndex,
            double availableRangeKm,
            double directDistanceKm
    ) {
        Set<Long> selectedStationIds = selected.stream()
                .map(candidate -> candidate.station().getId())
                .collect(java.util.stream.Collectors.toSet());
        double previousProgressKm = legIndex == 0
                ? 0
                : selected.get(legIndex - 1).distanceFromOriginKm();
        double nextProgressKm = legIndex < selected.size()
                ? selected.get(legIndex).distanceFromOriginKm()
                : directDistanceKm;
        List<Candidate> possible = candidates.stream()
                .filter(candidate -> !selectedStationIds.contains(candidate.station().getId()))
                .filter(candidate -> candidate.distanceFromOriginKm() > previousProgressKm + 1)
                .filter(candidate -> candidate.distanceFromOriginKm() < nextProgressKm - 1)
                .toList();
        if (possible.isEmpty()) {
            return null;
        }

        Coordinate previous = legIndex == 0
                ? origin
                : stationCoordinate(selected.get(legIndex - 1));
        Coordinate next = legIndex < selected.size()
                ? stationCoordinate(selected.get(legIndex))
                : destination;
        List<Coordinate> possibleCoordinates = possible.stream()
                .map(this::stationCoordinate)
                .toList();
        Map<Integer, double[]> metrics = new HashMap<>();
        for (OsrmClient.MatrixBatch batch : osrmClient.getBestMatrixTables(
                previous, possibleCoordinates, next, OsrmClient.RouteEngine.PRIMARY)) {
            OsrmTableResponse table = batch.response();
            if (table == null || !"Ok".equals(table.code()) || table.distances() == null) {
                continue;
            }
            int batchStationCount = batch.stationCoordinates().size();
            for (int localIndex = 0; localIndex < batchStationCount; localIndex++) {
                Double fromPreviousM = matrixValue(table.distances(), 0, localIndex);
                Double toNextM = matrixValue(table.distances(), localIndex + 1, batchStationCount);
                if (fromPreviousM != null && toNextM != null) {
                    metrics.put(batch.stationIndexes().get(localIndex),
                            new double[]{fromPreviousM / 1000.0, toNextM / 1000.0});
                }
            }
        }

        Candidate best = null;
        double bestScore = Double.MAX_VALUE;
        for (int index = 0; index < possible.size(); index++) {
            double[] metric = metrics.get(index);
            if (metric == null || metric[0] > availableRangeKm) {
                continue;
            }
            Candidate candidate = possible.get(index);
            double score = metric[1] + candidate.impactMinutes() * 0.05;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private Coordinate stationCoordinate(Candidate candidate) {
        return new Coordinate(candidate.station().getLatitude(), candidate.station().getLongitude());
    }

    private Itinerary itinerary(
            Coordinate origin,
            List<Candidate> selected,
            Coordinate destination,
            OsrmRoute directRoute
    ) {
        if (selected.isEmpty()) {
            return new Itinerary(
                    round(directRoute.distance() / 1000.0),
                    (int) Math.ceil(directRoute.duration() / 60.0),
                    selected,
                    List.of(round(directRoute.distance() / 1000.0))
            );
        }

        List<Coordinate> waypoints = new ArrayList<>();
        waypoints.add(origin);
        selected.stream()
                .map(candidate -> new Coordinate(
                        candidate.station().getLatitude(),
                        candidate.station().getLongitude()))
                .forEach(waypoints::add);
        waypoints.add(destination);

        OsrmRoute waypointRoute = getOsrmRoute(waypoints);
        if (waypointRoute.legs() == null || waypointRoute.legs().size() != selected.size() + 1) {
            throw new BadRequestException("The routing engine did not return every charging-stop route leg");
        }

        double totalDistanceKm = waypointRoute.distance() / 1000.0;
        double cumulativeDistanceKm = 0;
        List<Candidate> adjusted = new ArrayList<>();
        List<Double> legDistancesKm = waypointRoute.legs().stream()
                .map(leg -> round(leg.distance() / 1000.0))
                .toList();
        for (int index = 0; index < selected.size(); index++) {
            Candidate candidate = selected.get(index);
            cumulativeDistanceKm += waypointRoute.legs().get(index).distance() / 1000.0;
            adjusted.add(new Candidate(
                    candidate.station(),
                    candidate.connector(),
                    round(cumulativeDistanceKm),
                    round(Math.max(0, totalDistanceKm - cumulativeDistanceKm)),
                    candidate.detourKm(),
                    candidate.routeOffsetKm(),
                    candidate.waitMinutes(),
                    candidate.impactMinutes(),
                    candidate.restFriendly(),
                    candidate.selectionReason()
            ));
        }

        return new Itinerary(
                round(totalDistanceKm),
                (int) Math.ceil(waypointRoute.duration() / 60.0),
                List.copyOf(adjusted),
                legDistancesKm
        );
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

            double targetBattery;
            if (index + 1 >= selected.size()) {
                targetBattery = Math.min(80, Math.max(arrivalBattery + 10, trip.getMinimumArrivalBatteryPercent() + 5 + batteryNeeded));
            } else {
                targetBattery = Math.min(80, Math.max(arrivalBattery + 20, Math.min(80, trip.getMinimumArrivalBatteryPercent() + 10 + batteryNeeded)));
                targetBattery = Math.max(targetBattery, Math.min(80, arrivalBattery + 30));
            }

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
                    .demoData(candidate.station().isDemoData())
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
            Vehicle vehicle,
            double capacityKwh
    ) {
        double arrival = Math.max(
                trip.getMinimumArrivalBatteryPercent() + 1,
                replaced.getArrivalBatteryPercent());
        double target = Math.max(arrival + 10, Math.min(80, replaced.getTargetBatteryPercent()));
        double energy = capacityKwh * (target - arrival) / 100.0;
        VehicleChargingProfileService.ChargingProfile chargingProfile =
                vehicleChargingProfileService.forVehicle(vehicle);
        ChargingRouteOptimizer.ChargeEstimate chargeEstimate = chargingRouteOptimizer.estimateCharge(
                capacityKwh,
                candidate.connector().getPowerKw(),
                chargingProfile.maximumDcPowerKw(),
                chargingProfile.efficiency(),
                chargingProfile.curve(),
                arrival,
                target);
        return AutopilotStop.builder()
                .tripId(trip.getId())
                .sequenceNumber(replaced.getSequenceNumber())
                .stationId(candidate.station().getId())
                .stationName(candidate.station().getName())
                .stationAddress(candidate.station().getAddress())
                .connectorType(candidate.connector().getType().name())
                .powerKw(round(candidate.connector().getPowerKw()))
                .effectivePowerKw(round(chargeEstimate.effectivePowerKw()))
                .distanceFromOriginKm(candidate.distanceFromOriginKm())
                .routeOffsetKm(candidate.routeOffsetKm())
                .arrivalBatteryPercent(round(arrival))
                .targetBatteryPercent(round(target))
                .estimatedWaitMinutes(candidate.waitMinutes())
                .chargingMinutes(chargeEstimate.minutes())
                .connectionMinutes(4)
                .estimatedCost(roundMoney(energy * candidate.station().getPricePerKwh()))
                .demoData(candidate.station().isDemoData())
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

    private ChargingConnector bestConnector(ChargingStation station, Vehicle vehicle) {
        Set<String> supported = supportedConnectorNames(vehicle);
        return station.getConnectors().stream()
                .filter(ChargingConnector::isAvailable)
                .filter(connector -> !connector.isMaintenanceMode())
                .filter(connector -> connector.getStatus() == ChargerStatus.ONLINE)
                .filter(connector -> supported.contains(connector.getType().name()))
                .max(Comparator.comparingDouble(ChargingConnector::getPowerKw))
                .orElse(null);
    }

    private String normalizeConnector(String connector) {
        if (connector == null) return "CCS2";
        String normalized = connector.toUpperCase(Locale.ROOT)
                .replace("TYPE 2", "TYPE2")
                .replace("CHADEMO", "CHADEMO")
                .replaceAll("[^A-Z0-9/]", "");
        if (normalized.contains("BHARATDC001") || normalized.equals("GBT")) return "GB_T";
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
                .baseRouteDistanceKm(trip.getBaseRouteDistanceKm())
                .chargingDetourDistanceKm(trip.getChargingDetourDistanceKm())
                .estimatedDriveMinutes(trip.getEstimatedDriveMinutes())
                .baseDriveMinutes(trip.getBaseDriveMinutes())
                .chargingDetourMinutes(trip.getChargingDetourMinutes())
                .estimatedChargingMinutes(trip.getEstimatedChargingMinutes())
                .estimatedQueueMinutes(trip.getEstimatedQueueMinutes())
                .connectionOverheadMinutes(trip.getConnectionOverheadMinutes())
                .totalDurationMinutes(trip.getTotalDurationMinutes())
                .estimatedChargingCost(trip.getEstimatedChargingCost())
                .estimatedArrivalBatteryPercent(trip.getEstimatedArrivalBatteryPercent())
                .feasibleAlternativesCompared(trip.getFeasibleAlternativesCompared())
                .optimizationSummary(trip.getOptimizationSummary())
                .routeEngine(trip.getRouteEngine())
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
                        .remainingRangeKm(round(capacity * trip.getCurrentBatteryPercent() / 100.0
                                / vehicleEnergyPerKmKwh(vehicle)))
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
                .effectivePowerKw(stop.getEffectivePowerKw())
                .distanceFromOriginKm(stop.getDistanceFromOriginKm())
                .routeOffsetKm(stop.getRouteOffsetKm())
                .arrivalBatteryPercent(stop.getArrivalBatteryPercent())
                .targetBatteryPercent(stop.getTargetBatteryPercent())
                .estimatedWaitMinutes(stop.getEstimatedWaitMinutes())
                .chargingMinutes(stop.getChargingMinutes())
                .connectionMinutes(stop.getConnectionMinutes())
                .estimatedCost(stop.getEstimatedCost())
                .demoData(stop.isDemoData())
                .selectionReason(stop.getSelectionReason())
                .timingScore(timingScore(stop.getEstimatedWaitMinutes()))
                .timingLabel(timingLabel(stop.getEstimatedWaitMinutes()))
                .status(stop.getStatus())
                .selectionType(stop.getSelectionType())
                .replacesStationId(stop.getReplacesStationId())
                .replacesStationName(stop.getReplacesStationName())
                .rerouteReason(stop.getRerouteReason())
                .additionalDistanceKm(stop.getAdditionalDistanceKm())
                .additionalMinutes(stop.getAdditionalMinutes())
                .additionalCost(stop.getAdditionalCost())
                .removalReason(stop.getRemovalReason())
                .replacedByStationId(stop.getReplacedByStationId())
                .replacedByStationName(stop.getReplacedByStationName())
                .originalStopIndex(stop.getOriginalStopIndex())
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

    private VehicleRecommendationOptionResponse recommendationOption(
            Vehicle vehicle,
            double currentBattery,
            AutopilotPlanResponse plan,
            boolean feasible,
            String reason
    ) {
        List<String> connectors = supportedConnectorNames(vehicle).stream().sorted().toList();
        boolean hasDcConnector = connectors.stream()
                .anyMatch(connector -> connector.equals("CCS2")
                        || connector.equals("CHADEMO") || connector.equals("GB_T"));
        double maximumPower = hasDcConnector
                ? validPositive(vehicle.getMaxDcChargePowerKw(), 50)
                : validPositive(vehicle.getMaxAcChargePowerKw(), 7.2);
        return VehicleRecommendationOptionResponse.builder()
                .vehicleId(vehicle.getId())
                .vehicleName(vehicle.getMakeAndModel())
                .registrationNumber(vehicle.getRegistrationNumber())
                .supportedConnectors(connectors)
                .batteryCapacityKwh(round(batteryCapacity(vehicle)))
                .currentBatteryPercent(round(currentBattery))
                .efficiencyWhPerKm(round(vehicleEnergyPerKmKwh(vehicle) * 1000))
                .maximumChargingPowerKw(round(maximumPower))
                .feasible(feasible)
                .reason(reason)
                .compatibleChargersEvaluated(plan == null ? 0 : plan.getCompatibleChargersEvaluated())
                .chargingStops(plan == null || plan.getStops() == null ? 0 : plan.getStops().size())
                .journeyMinutes(plan == null ? 0 : plan.getTotalDurationMinutes())
                .chargingMinutes(plan == null ? 0 : plan.getEstimatedChargingMinutes())
                .estimatedCost(plan == null ? 0 : plan.getEstimatedChargingCost())
                .arrivalBatteryPercent(plan == null ? 0 : plan.getEstimatedArrivalBatteryPercent())
                .withinBudget(plan != null && plan.isWithinBudget())
                .deadlineFeasible(plan != null && plan.isDeadlineFeasible())
                .build();
    }

    private String feasibleVehicleReason(AutopilotPlanResponse plan, String optimization) {
        return switch (optimization) {
            case "COST" -> "Feasible with " + plan.getStops().size()
                    + " compatible charging stop(s) at an estimated ₹"
                    + roundMoney(plan.getEstimatedChargingCost()) + ".";
            case "BALANCED" -> "Feasible balance of " + formatMinutesForReason(plan.getTotalDurationMinutes())
                    + ", ₹" + roundMoney(plan.getEstimatedChargingCost()) + " and "
                    + plan.getStops().size() + " charging stop(s).";
            default -> "Feasible in " + formatMinutesForReason(plan.getTotalDurationMinutes())
                    + " with " + plan.getStops().size() + " compatible charging stop(s).";
        };
    }

    private String recommendedVehicleReason(AutopilotPlanResponse plan, String optimization) {
        return switch (optimization) {
            case "COST" -> "Lowest estimated charging cost among feasible cars: ₹"
                    + roundMoney(plan.getEstimatedChargingCost()) + " with "
                    + plan.getStops().size() + " stop(s).";
            case "BALANCED" -> "Best feasible balance of total time, charging cost and stop count: "
                    + formatMinutesForReason(plan.getTotalDurationMinutes()) + ", ₹"
                    + roundMoney(plan.getEstimatedChargingCost()) + " and "
                    + plan.getStops().size() + " stop(s).";
            default -> "Lowest estimated total journey time among feasible cars: "
                    + formatMinutesForReason(plan.getTotalDurationMinutes()) + " with "
                    + plan.getStops().size() + " stop(s).";
        };
    }

    private String infeasiblePlanReason(AutopilotPlanResponse plan) {
        if (!plan.isWithinBudget() && !plan.isDeadlineFeasible()) {
            return "A battery-safe route exists, but it exceeds the charging budget and misses the arrival deadline.";
        }
        if (!plan.isWithinBudget()) {
            return "A battery-safe route exists, but its estimated ₹"
                    + roundMoney(plan.getEstimatedChargingCost()) + " charging cost exceeds the budget.";
        }
        if (!plan.isDeadlineFeasible()) {
            return "A battery-safe route exists, but it misses the arrival deadline by "
                    + plan.getDeadlineMinutesLate() + " minutes.";
        }
        return "The route cannot maintain the requested arrival battery reserve.";
    }

    private String planningFailureReason(Vehicle vehicle, BadRequestException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        String normalized = message.toLowerCase(Locale.ROOT);
        String connectors = String.join(" + ", supportedConnectorNames(vehicle).stream().sorted().toList());
        if (normalized.contains("charger sequence")
                || normalized.contains("safely reachable")
                || normalized.contains("safely bridge")
                || normalized.contains("safe route")) {
            return "No reachable compatible " + connectors
                    + " charging chain can maintain the requested battery reserve on this road corridor.";
        }
        if (normalized.contains("no online charger") || normalized.contains("connector")) {
            return "Compatible " + connectors + " charging coverage is insufficient on this corridor.";
        }
        return message.isBlank() ? "The route could not be evaluated for this vehicle." : message;
    }

    private String formatMinutesForReason(int minutes) {
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private Set<String> supportedConnectorNames(Vehicle vehicle) {
        if (vehicle.getSupportedConnectors() != null && !vehicle.getSupportedConnectors().isEmpty()) {
            return vehicle.getSupportedConnectors().stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        }
        return Set.of(normalizeConnector(vehicle.getConnectorType()));
    }

    private double vehicleEnergyPerKmKwh(Vehicle vehicle) {
        Double efficiency = vehicle.getEfficiencyWhPerKm();
        return efficiency != null && Double.isFinite(efficiency) && efficiency >= 50 && efficiency <= 500
                ? efficiency / 1000.0
                : ENERGY_PER_KM_KWH;
    }

    private double validPositive(Double value, double fallback) {
        return value != null && Double.isFinite(value) && value > 0 ? value : fallback;
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
            case COMMUTE -> "Daily commute: optimize for low queue delay and repeat reliability.";
            case DESTINATION_CHARGING -> "Destination charging: arrive near " + destination
                    + " with minimal highway stops and charge at the finish.";
            case GENERAL -> "General journey: balance safe range, detour, live queue, charging speed and price.";
        };
    }

    private String selectionReason(
            TripPurpose purpose,
            ChargingStation station,
            double toDestination,
            boolean restFriendly,
            MemorySignal signal
    ) {
        String base = switch (purpose) {
            case MALL_VISIT, DESTINATION_CHARGING -> round(toDestination) + " km from destination";
            case REST_STOP -> restFriendly ? "Rest & food friendly" : "Fast charging on corridor";
            case COMMUTE -> station.getQueueCount() == 0 ? "Zero wait commuter stop" : station.getQueueCount() + " in queue";
            default -> station.getAvailableSlots() + " slot(s) available · " + round(station.getPricePerKwh()) + " ₹/kWh";
        };
        if (signal.successes() > 0 && signal.failures() == 0) {
            return base + " · route memory: reliable stop (" + signal.successes() + " success)";
        }
        if (signal.failures() > 0) {
            return base + " · route memory: flagged " + signal.failures() + " issue(s)";
        }
        return base;
    }

    private String telemetryState(AutopilotTripStatus status) {
        return switch (status) {
            case RESERVED -> "STANDBY";
            case MONITORING -> "DRIVING";
            case REROUTED -> "REROUTING";
            case REROUTE_APPROVAL_REQUIRED -> "WAITING_FOR_DRIVER";
            case REPLAN_REQUIRED -> "SAFE_STOP_REQUIRED";
            case PAYMENT_REQUIRED -> "CHARGING_COMPLETE";
            case COMPLETED -> "PARKED";
            case CANCELLED -> "DISCONNECTED";
        };
    }

    private String statusLabel(AutopilotTripStatus status) {
        return switch (status) {
            case RESERVED -> "CHARGER RESERVED";
            case MONITORING -> "AUTONOMOUS MONITORING";
            case REROUTED -> "AUTOMATICALLY REROUTED";
            case REROUTE_APPROVAL_REQUIRED -> "REROUTE APPROVAL NEEDED";
            case REPLAN_REQUIRED -> "SAFE REPLAN NEEDED";
            case PAYMENT_REQUIRED -> "ACTION REQUIRED";
            case COMPLETED -> "JOURNEY COMPLETED";
            case CANCELLED -> "CANCELLED";
        };
    }

    private void remember(AutopilotTrip trip, Long stationId, RouteExperienceOutcome outcome,
                          String detail, Integer rating, Integer delayMinutes) {
        if (stationId == null) return;
        experienceRepository.save(RouteExperience.builder()
                .userId(trip.getUserId())
                .tripId(trip.getId())
                .stationId(stationId)
                .origin(trip.getOrigin())
                .destination(trip.getDestination())
                .originKey(routeKey(trip.getOrigin()))
                .destinationKey(routeKey(trip.getDestination()))
                .outcome(outcome)
                .detail(detail)
                .rating(rating)
                .delayMinutes(delayMinutes)
                .build());
    }

    private RouteMemory routeMemory(String origin, String destination) {
        List<RouteExperience> experiences = experienceRepository
                .findTop30ByOriginKeyAndDestinationKeyOrderByCreatedAtDesc(routeKey(origin), routeKey(destination));
        Map<Long, MemorySignal> signals = new HashMap<>();
        for (RouteExperience item : experiences) {
            if (item.getStationId() == null) continue;
            signals.compute(item.getStationId(), (k, v) -> {
                MemorySignal current = v == null ? MemorySignal.EMPTY : v;
                int successes = current.successes() + (item.getOutcome() == RouteExperienceOutcome.SUCCESS ? 1 : 0);
                int failures = current.failures() + (item.getOutcome() == RouteExperienceOutcome.SUCCESS ? 0 : 1);
                int delays = current.totalDelays() + (item.getDelayMinutes() == null ? 0 : item.getDelayMinutes());
                int lowRatings = current.lowRatings() + (item.getRating() != null && item.getRating() <= 2 ? 1 : 0);
                return new MemorySignal(successes, failures, delays, lowRatings);
            });
        }
        return new RouteMemory(experiences.size(), signals);
    }

    private RouteExperienceResponse mapExperience(RouteExperience experience) {
        return new RouteExperienceResponse(
                experience.getId(),
                experience.getTripId(),
                experience.getStationId(),
                experience.getOrigin(),
                experience.getDestination(),
                experience.getOutcome(),
                experience.getDetail(),
                experience.getRating(),
                experience.getDelayMinutes(),
                experience.getCreatedAt()
        );
    }

    private String routeKey(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ").trim();
        return normalized.isBlank() ? "default" : normalized;
    }

    private String normalizedOptimization(String optimizeFor) {
        if (optimizeFor == null) return "TIME";
        String normalized = optimizeFor.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "COST", "BALANCED" -> normalized;
            default -> "TIME";
        };
    }

    private String normalizedAutonomyMode(String autonomyMode) {
        if (autonomyMode == null) return "ASK_BEFORE_ACTIONS";
        String normalized = autonomyMode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RECOMMEND_ONLY", "FULL_AUTOPILOT" -> normalized;
            default -> "ASK_BEFORE_ACTIONS";
        };
    }

    private String normalizedIdempotencyKey(String key) {
        if (key != null && !key.isBlank()) return key.trim();
        return "TRIP-" + UUID.randomUUID();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String displayDeadline(String deadline) {
        return deadline == null || deadline.isBlank() ? "estimated ETA" : deadline;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record BaseRoute(
            OsrmRoute route,
            OsrmClient.RouteEngine engine
    ) {}

    private record RouteBounds(
            double minimumLatitude,
            double maximumLatitude,
            double minimumLongitude,
            double maximumLongitude
    ) {
        private static final RouteBounds INDIA = new RouteBounds(5, 39, 67, 99);

        boolean contains(double latitude, double longitude) {
            return latitude >= minimumLatitude && latitude <= maximumLatitude
                    && longitude >= minimumLongitude && longitude <= maximumLongitude;
        }
    }

    private record PlanningContext(
            BaseRoute baseRoute,
            List<Candidate> candidates,
            List<Candidate> selected,
            ChargingRouteOptimizer.OptimizationResult optimized,
            OsrmRoute finalRoute,
            OsrmClient.RouteEngine finalRouteEngine,
            OsrmClient.MatrixSelection matrixSelection
    ) {}

    private record Candidate(
            ChargingStation station,
            ChargingConnector connector,
            double distanceFromOriginKm,
            double distanceToDestinationKm,
            double detourKm,
            double routeOffsetKm,
            int waitMinutes,
            double impactMinutes,
            boolean restFriendly,
            String selectionReason
    ) {}

    private record Itinerary(
            double distanceKm,
            int drivingMinutes,
            List<Candidate> selected,
            List<Double> legDistancesKm
    ) {}

    private record MemorySignal(int successes, int failures, int totalDelays, int lowRatings) {
        static final MemorySignal EMPTY = new MemorySignal(0, 0, 0, 0);
        double averageDelayMinutes() {
            int total = successes + failures;
            return total == 0 ? 0 : (double) totalDelays / total;
        }
    }

    private record RouteMemory(int totalExperiences, Map<Long, MemorySignal> stationSignals) {
        String summary() {
            if (totalExperiences == 0) return "No previous journey experience exists for this corridor yet.";
            return totalExperiences + " past journey outcome" + (totalExperiences == 1 ? "" : "s")
                    + " were retrieved to score reliability, queue delays and charger faults on this corridor.";
        }
    }

    @Transactional
    public void resetDemoStations() {
        List<ChargingStation> stations = stationRepository.findAll();
        for (ChargingStation s : stations) {
            if (s.isDemoData()) {
                s.setAvailability(StationAvailability.AVAILABLE);
                s.setStatus(StationStatus.ACTIVE);
                s.setEmergencyDisabled(false);
                if (s.getConnectors() != null) {
                    for (ChargingConnector c : s.getConnectors()) {
                        c.setAvailable(true);
                        c.setMaintenanceMode(false);
                        c.setStatus(ChargerStatus.ONLINE);
                        c.setFaultCode(null);
                        c.setHealthScore(98);
                    }
                }
            }
        }
        stationRepository.saveAll(stations);
    }

    private record VehicleEvaluation(
            Vehicle vehicle,
            AutopilotPlanResponse plan,
            VehicleRecommendationOptionResponse option
    ) {}
}
