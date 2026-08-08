package com.vidyut.routing.dto;

import com.vidyut.station.dto.StationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteStationResponse {
    private StationResponse station;
    private double distanceFromOriginKm;
    private int recommendedChargeMinutes;
}
