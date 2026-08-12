package com.vidyut.vehicle.dto;

import com.vidyut.vehicle.entity.VehicleConnectionStatus;
import com.vidyut.vehicle.entity.VehicleTelemetrySource;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleUpdateRequest {

    @Min(value = 0, message = "Battery percentage cannot be below zero")
    @Max(value = 100, message = "Battery percentage cannot exceed 100")
    private Integer batteryPercent;

    @DecimalMin(value = "0.0", message = "Remaining range cannot be negative")
    private Double remainingRangeKm;

    private VehicleConnectionStatus connectionStatus;
    private Boolean charging;
    private Boolean bluetoothSupported;
    private Boolean androidAutoSupported;
    private Boolean appleCarPlaySupported;
    private String bluetoothDeviceName;
    private String bluetoothDeviceId;
    private String bluetoothServiceUuid;
    private Boolean btSessionControlEnabled;
    private Boolean btSimulatorEnabled;
    private String lastChargingStation;
    private String lastChargingAddress;
    private LocalDateTime lastChargedAt;
    private VehicleTelemetrySource telemetrySource;
}
