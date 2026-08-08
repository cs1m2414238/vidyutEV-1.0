package com.vidyut.routing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutePlanResponse {
    private String origin;
    private String destination;
    private double totalDistanceKm;
    private int totalDurationMinutes;
    private List<RouteStationResponse> recommendedChargingStops;
}
