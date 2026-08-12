package com.vidyut.vehicle.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String makeAndModel;

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    private String batteryCapacity;
    private String connectorType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VehicleConnectionStatus connectionStatus = VehicleConnectionStatus.UNKNOWN;

    private Integer batteryPercent;
    private Double remainingRangeKm;
    private Boolean charging;
    private Boolean bluetoothSupported;
    private Boolean androidAutoSupported;
    private Boolean appleCarPlaySupported;
    private String bluetoothDeviceName;
    private String bluetoothDeviceId;
    private String bluetoothServiceUuid;

    @Builder.Default
    private boolean btSessionControlEnabled = false;

    @Builder.Default
    private boolean btSimulatorEnabled = false;
    private String lastChargingStation;
    private String lastChargingAddress;
    private LocalDateTime lastChargedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "telemetry_source")
    @Builder.Default
    private VehicleTelemetrySource telemetrySource = VehicleTelemetrySource.NOT_AVAILABLE;

    private LocalDateTime telemetryUpdatedAt;
}
