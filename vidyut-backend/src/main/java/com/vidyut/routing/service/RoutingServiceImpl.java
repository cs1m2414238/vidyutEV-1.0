package com.vidyut.routing.service;

import com.vidyut.routing.dto.RoutePlanRequest;
import com.vidyut.routing.dto.RoutePlanResponse;
import com.vidyut.routing.dto.RouteStationResponse;
import com.vidyut.station.service.ChargingStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoutingServiceImpl implements RoutingService {

    private final ChargingStationService stationService;

    @Override
    public RoutePlanResponse planRoute(RoutePlanRequest request) {
        var stations = stationService.getAllStations();
        List<RouteStationResponse> recommended = stations.stream()
                .limit(2)
                .map(s -> RouteStationResponse.builder()
                        .station(s)
                        .distanceFromOriginKm(8.5)
                        .recommendedChargeMinutes(25)
                        .build())
                .collect(Collectors.toList());

        return RoutePlanResponse.builder()
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .totalDistanceKm(24.5)
                .totalDurationMinutes(45)
                .recommendedChargingStops(recommended)
                .build();
    }
}
