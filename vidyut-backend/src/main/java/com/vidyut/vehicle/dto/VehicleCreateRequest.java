package com.vidyut.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleCreateRequest {

    @NotBlank(message = "Vehicle make and model is required")
    private String makeAndModel;

    @NotBlank(message = "Registration number is required")
    private String registrationNumber;

    private String batteryCapacity;

    private String connectorType;
}
