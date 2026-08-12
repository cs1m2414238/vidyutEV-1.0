package com.vidyut.routing.service;

import com.vidyut.autopilot.entity.RouteExperience;
import com.vidyut.autopilot.entity.RouteExperienceOutcome;
import com.vidyut.autopilot.entity.TripPurpose;
import com.vidyut.autopilot.repository.RouteExperienceRepository;
import com.vidyut.routing.dto.*;
import com.vidyut.station.dto.StationResponse;
import com.vidyut.station.entity.*;
import com.vidyut.station.service.ChargingStationService;
import com.vidyut.booking.dto.*;
import com.vidyut.booking.entity.*;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.booking.service.BookingService;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RoutingServiceImpl implements RoutingService {

    private final ChargingStationService stationService;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final RouteExperienceRepository experienceRepository;

    private static final Map<String, double[]> KNOWN_LOCATIONS = Map.of(
            "lucknow", new double[]{26.8467, 80.9462},
            "kanpur", new double[]{26.4499, 80.3319},
            "delhi", new double[]{28.6139, 77.2090},
            "jaipur", new double[]{26.9124, 75.7873},
            "mumbai", new double[]{19.0760, 72.8777},
            "agra", new double[]{27.1767, 78.0081}
    );

    @Override
    public RoutePlanResponse planRoute(RoutePlanRequest request, Long userId) {
        Vehicle vehicle = resolveVehicle(userId, request.getVehicleId());
        double[] origin = resolveCoordinates(request.getOriginLatitude(), request.getOriginLongitude(), request.getOrigin());
        double[] destination = resolveCoordinates(request.getDestinationLatitude(), request.getDestinationLongitude(), request.getDestination());
        double totalDistance = request.getDestinationDistanceKm() != null && request.getDestinationDistanceKm() > 0
                ? request.getDestinationDistanceKm() : round(distance(origin[0], origin[1], destination[0], destination[1]) * 1.18);
        double batteryPercent = request.getCurrentBatteryPercent() > 0
                ? Math.min(100, request.getCurrentBatteryPercent())
                : Optional.ofNullable(vehicle.getBatteryPercent()).orElse(80);
        double reserve = request.getReserveBatteryPercent() == null ? 10
                : Math.max(5, Math.min(30, request.getReserveBatteryPercent()));
        double fullRange = estimateFullRange(vehicle, batteryPercent);
        double usableRange = round(fullRange * Math.max(0, batteryPercent - reserve) / 100.0);
        boolean withinRange = totalDistance <= usableRange;
        TripPurpose purpose = resolvePurpose(request.getTripPurpose(), request.getDestination());
        List<RouteExperience> routeMemory = experienceRepository
                .findTop30ByOriginKeyAndDestinationKeyOrderByCreatedAtDesc(routeKey(request.getOrigin()), routeKey(request.getDestination()));
        Map<Long, List<RouteExperience>> memoryByStation = routeMemory.stream()
                .filter(experience -> experience.getStationId() != null)
                .collect(java.util.stream.Collectors.groupingBy(RouteExperience::getStationId));

        List<RouteStationResponse> candidates = rankStations(stationService.getAllStations(), vehicle, origin,
                destination, usableRange, purpose, memoryByStation);
        boolean purposeStopUseful = purpose == TripPurpose.MALL_VISIT || purpose == TripPurpose.REST_STOP
                || purpose == TripPurpose.DESTINATION_CHARGING;
        List<RouteStationResponse> recommended = withinRange && !purposeStopUseful ? List.of()
                : candidates.stream().limit(requiredStops(totalDistance, Math.max(usableRange, 50))).toList();
        double arrival = Math.max(reserve, batteryPercent - (totalDistance / Math.max(fullRange, 1) * 100));

        return RoutePlanResponse.builder()
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .tripPurpose(purpose.name())
                .purposeSummary(purposeSummary(purpose, request.getDestination()))
                .pastExperiencesUsed(routeMemory.size())
                .totalDistanceKm(totalDistance)
                .totalDurationMinutes((int) Math.ceil(totalDistance / 55.0 * 60)
                        + recommended.stream().mapToInt(RouteStationResponse::getRecommendedChargeMinutes).sum())
                .recommendedChargingStops(recommended)
                .vehicleId(vehicle.getId()).usableRangeKm(usableRange).reserveBatteryPercent(reserve)
                .estimatedArrivalBatteryPercent(round(arrival)).destinationWithinRange(withinRange)
                .routeSource("DETERMINISTIC_RANGE_AND_AVAILABILITY")
                .externalMapsUrl("https://www.google.com/maps/dir/?api=1&origin=" + encode(request.getOrigin())
                        + "&destination=" + encode(request.getDestination()))
                .build();
    }

    @Override
    public List<RouteStationResponse> alternatives(Long userId, Long stationId, Long vehicleId) {
        StationResponse current = stationService.getStationById(stationId);
        Vehicle vehicle = resolveVehicle(userId, vehicleId);
        double[] origin = {current.getLatitude(), current.getLongitude()};
        return rankStations(stationService.getAllStations().stream().filter(s -> !s.getId().equals(stationId)).toList(),
                vehicle, origin, origin, Double.MAX_VALUE, TripPurpose.GENERAL, Map.of()).stream().limit(5).toList();
    }

    @Override
    public RouteStatusResponse routeStatus(Long userId, Long bookingId) {
        Booking booking = ownedBooking(userId, bookingId);
        StationResponse station = stationService.getStationById(booking.getStationId());
        boolean divert = station.getStatus() != StationStatus.ACTIVE || station.isEmergencyDisabled()
                || station.getAvailableSlots() == 0;
        return RouteStatusResponse.builder().bookingId(bookingId).stationId(station.getId())
                .stationStatus(station.getLiveStatus()).diversionRecommended(divert)
                .reason(divert ? "The booked station is offline or full" : "The booked station is operating normally")
                .alternatives(divert ? alternatives(userId, station.getId(), booking.getVehicleId()) : List.of()).build();
    }

    @Override
    public DiversionResponse divert(Long userId, Long bookingId, Long alternativeStationId) {
        Booking current = ownedBooking(userId, bookingId);
        if (current.getStatus() == BookingStatus.IN_PROGRESS || current.getStatus() == BookingStatus.COMPLETED) {
            throw new BadRequestException("An active or completed charging session cannot be diverted");
        }
        List<RouteStationResponse> compatibleAlternatives = alternatives(
                userId, current.getStationId(), current.getVehicleId());
        RouteStationResponse selectedRoute = compatibleAlternatives.stream()
                .filter(candidate -> candidate.getStation().getId().equals(alternativeStationId))
                .findFirst().orElseThrow(() -> new BadRequestException(
                        "The selected station is not a compatible live diversion"));
        double bestDetour = compatibleAlternatives.stream().mapToDouble(RouteStationResponse::getDetourKm)
                .min().orElse(selectedRoute.getDetourKm());
        if (selectedRoute.getDetourKm() > Math.max(5, bestDetour * 1.15)) {
            throw new BadRequestException("Choose an alternative within 115% of the best available detour");
        }
        StationResponse alternative = selectedRoute.getStation();
        if (alternative.getStatus() != StationStatus.ACTIVE || alternative.getAvailableSlots() == 0) {
            throw new BadRequestException("The selected alternative is not available");
        }
        if (current.getVehicleId() != null) {
            Vehicle vehicle = resolveVehicle(userId, current.getVehicleId());
            if (!connectorMatches(alternative, vehicle.getConnectorType())) {
                throw new BadRequestException("The alternative station does not support this vehicle connector");
            }
        }
        bookingService.cancelBookingWithoutFee(current.getId(), userId,
                "The booked station became unavailable; your reservation is being moved.");
        LocalDateTime replacementStart = current.getStartTime() != null && current.getStartTime().isAfter(LocalDateTime.now())
                ? current.getStartTime() : LocalDateTime.now().plusMinutes(5);
        BookingResponse replacement = bookingService.createBooking(BookingCreateRequest.builder()
                .stationId(alternativeStationId).vehicleId(current.getVehicleId()).startTime(replacementStart)
                .durationMinutes(current.getDurationMinutes() > 0 ? current.getDurationMinutes()
                        : Math.max(1, current.getDurationHours()) * 60).build(), userId);
        return DiversionResponse.builder().cancelledBooking(bookingService.getBookingById(current.getId(), userId))
                .replacementBooking(replacement).message("Booking moved to " + alternative.getName()).build();
    }

    private List<RouteStationResponse> rankStations(List<StationResponse> stations, Vehicle vehicle, double[] origin,
                                                    double[] destination, double reachableRange, TripPurpose purpose,
                                                    Map<Long, List<RouteExperience>> memoryByStation) {
        return stations.stream()
                .filter(station -> station.getStatus() == StationStatus.ACTIVE && !station.isEmergencyDisabled())
                .filter(station -> station.getAvailableSlots() > 0)
                .filter(station -> connectorMatches(station, vehicle.getConnectorType()))
                .map(station -> {
                    double fromOrigin = distance(origin[0], origin[1], station.getLatitude(), station.getLongitude());
                    double detour = pointToRouteDetour(origin, destination, station);
                    double power = station.getConnectors().stream().filter(c -> c.getStatus() == ChargerStatus.ONLINE)
                            .mapToDouble(ChargingConnector::getPowerKw).max().orElse(7.4);
                    List<RouteExperience> stationMemory = memoryByStation.getOrDefault(station.getId(), List.of());
                    long issues = stationMemory.stream().filter(item -> item.getOutcome() != RouteExperienceOutcome.SUCCESS).count();
                    long successes = stationMemory.size() - issues;
                    boolean restFriendly = isRestFriendly(station.getAmenities());
                    double destinationDistance = distance(station.getLatitude(), station.getLongitude(), destination[0], destination[1]);
                    String purposeReason = switch (purpose) {
                        case MALL_VISIT, DESTINATION_CHARGING -> round(destinationDistance) + " km from destination";
                        case REST_STOP -> restFriendly ? "Rest and food amenities available" : "Best reachable route stop";
                        case COMMUTE -> "Low-delay commute option";
                        default -> station.getAvailableSlots() + " compatible connector(s)";
                    };
                    String memoryReason = stationMemory.isEmpty() ? "" : "; route memory: " + successes
                            + " success, " + issues + " issue signal(s)";
                    return RouteStationResponse.builder().station(station).distanceFromOriginKm(round(fromOrigin))
                            .detourKm(round(detour)).etaMinutes((int) Math.ceil(fromOrigin / 45.0 * 60))
                            .availableSlots(station.getAvailableSlots()).connectorMatched(true)
                            .recommendedChargeMinutes((int) Math.max(15, Math.ceil(20 / power * 60)))
                            .estimatedChargingCost(round(20 * station.getPricePerKwh()))
                            .reason(purposeReason + ", " + round(detour) + " km detour" + memoryReason)
                            .build();
                })
                .filter(stop -> stop.getDistanceFromOriginKm() <= reachableRange || reachableRange == Double.MAX_VALUE)
                .sorted(Comparator.comparingDouble((RouteStationResponse stop) -> purposeScore(stop, purpose, destination, memoryByStation))
                        .thenComparing(Comparator.comparingInt(RouteStationResponse::getAvailableSlots).reversed())
                        .thenComparingDouble(stop -> stop.getStation().getPricePerKwh()))
                .toList();
    }

    private Vehicle resolveVehicle(Long userId, Long vehicleId) {
        if (vehicleId != null) return vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this account"));
        return vehicleRepository.findByUserId(userId).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("Add a vehicle before planning a trip"));
    }

    private Booking ownedBooking(Long userId, Long bookingId) {
        return bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for this account"));
    }

    private boolean connectorMatches(StationResponse station, String connectorType) {
        if (connectorType == null || connectorType.isBlank()) return true;
        String normalized = normalizeConnector(connectorType);
        return station.getConnectors().stream().anyMatch(connector ->
                normalizeConnector(connector.getType().name()).equals(normalized));
    }

    private String normalizeConnector(String value) {
        return value.replace("_", "").replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private double estimateFullRange(Vehicle vehicle, double batteryPercent) {
        if (vehicle.getRemainingRangeKm() != null && vehicle.getRemainingRangeKm() > 0 && batteryPercent > 0) {
            return vehicle.getRemainingRangeKm() * 100.0 / batteryPercent;
        }
        double capacity = parseNumber(vehicle.getBatteryCapacity());
        return capacity > 0 ? capacity * 6.2 : 300;
    }

    private double[] resolveCoordinates(Double lat, Double lng, String label) {
        if (lat != null && lng != null) return new double[]{lat, lng};
        String normalized = label == null ? "" : label.toLowerCase(Locale.ROOT);
        return KNOWN_LOCATIONS.entrySet().stream().filter(entry -> normalized.contains(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(KNOWN_LOCATIONS.get("lucknow"));
    }

    private double pointToRouteDetour(double[] origin, double[] destination, StationResponse station) {
        double direct = distance(origin[0], origin[1], destination[0], destination[1]);
        double via = distance(origin[0], origin[1], station.getLatitude(), station.getLongitude())
                + distance(station.getLatitude(), station.getLongitude(), destination[0], destination[1]);
        return Math.max(0, via - direct);
    }

    private int requiredStops(double distance, double usableRange) {
        return Math.max(1, Math.min(3, (int) Math.ceil(distance / usableRange) - 1));
    }

    private TripPurpose resolvePurpose(String explicitPurpose, String destination) {
        if (explicitPurpose != null && !explicitPurpose.isBlank()) {
            try { return TripPurpose.valueOf(explicitPurpose.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { /* Infer below. */ }
        }
        String intent = destination == null ? "" : destination.toLowerCase(Locale.ROOT);
        if (intent.matches(".*(mall|shopping|market|cinema).*")) return TripPurpose.MALL_VISIT;
        if (intent.matches(".*(rest|food|cafe|hotel|washroom).*")) return TripPurpose.REST_STOP;
        if (intent.matches(".*(office|work|college|school).*")) return TripPurpose.COMMUTE;
        return TripPurpose.GENERAL;
    }

    private String purposeSummary(TripPurpose purpose, String destination) {
        return switch (purpose) {
            case MALL_VISIT -> "A charger close to " + destination + " is preferred so shopping and charging happen together.";
            case REST_STOP -> "An on-route charger with food, restroom, lounge or hotel amenities is preferred.";
            case COMMUTE -> "Low queue and repeat reliability are prioritized for the daily commute.";
            case DESTINATION_CHARGING -> "The final charging option is kept close to " + destination + ".";
            case GENERAL -> "Stops balance range safety, detour, queue, compatibility and price.";
        };
    }

    private double purposeScore(RouteStationResponse stop, TripPurpose purpose, double[] destination,
            Map<Long, List<RouteExperience>> memoryByStation) {
        StationResponse station = stop.getStation();
        List<RouteExperience> memories = memoryByStation.getOrDefault(station.getId(), List.of());
        long failures = memories.stream().filter(item -> item.getOutcome() != RouteExperienceOutcome.SUCCESS).count();
        long successes = memories.size() - failures;
        double memoryPenalty = failures * 55 - successes * 4;
        double purposePenalty = switch (purpose) {
            case MALL_VISIT, DESTINATION_CHARGING -> distance(station.getLatitude(), station.getLongitude(), destination[0], destination[1]) * 2;
            case REST_STOP -> isRestFriendly(station.getAmenities()) ? 0 : 120;
            case COMMUTE -> station.getQueueCount() * 9.0 + station.getOccupancyPercent() * 0.2;
            default -> 0;
        };
        return stop.getDetourKm() + purposePenalty + memoryPenalty;
    }

    private boolean isRestFriendly(String amenities) {
        return amenities != null && amenities.toLowerCase(Locale.ROOT)
                .matches(".*(restroom|restaurant|food|cafe|lounge|hotel|washroom).*" );
    }

    private String routeKey(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ").trim();
        for (String city : KNOWN_LOCATIONS.keySet()) if (normalized.contains(city)) return city;
        return normalized.isBlank() ? "unknown" : normalized.substring(0, Math.min(120, normalized.length()));
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double p = Math.PI / 180;
        double a = 0.5 - Math.cos((lat2 - lat1) * p) / 2
                + Math.cos(lat1 * p) * Math.cos(lat2 * p) * (1 - Math.cos((lon2 - lon1) * p)) / 2;
        return 12742 * Math.asin(Math.sqrt(a));
    }

    private double parseNumber(String value) {
        if (value == null) return 0;
        try { return Double.parseDouble(value.replaceAll("[^0-9.]", "")); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private double round(double value) { return Math.round(value * 10.0) / 10.0; }
    private String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
}
