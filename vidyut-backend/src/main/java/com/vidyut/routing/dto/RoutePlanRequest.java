package com.vidyut.routing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutePlanRequest {

    @NotBlank(message = "Origin location is required")
    private String origin;

    @NotBlank(message = "Destination location is required")
    private String destination;

    private double currentBatteryPercent;
}
