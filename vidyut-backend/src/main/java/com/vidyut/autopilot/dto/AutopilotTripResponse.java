package com.vidyut.autopilot.dto;

import com.vidyut.autopilot.entity.AutopilotTripStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotTripResponse {
    private Long id;
    private String idempotencyKey;
    private String goal;
    private String origin;
    private String destination;
    private String arrivalDeadline;
    private String optimizeFor;
    private String tripPurpose;
    private String memorySummary;
    private String autonomyMode;
    private double minimumArrivalBatteryPercent;
    private double maximumChargingBudget;
    private double totalDistanceKm;
    private int estimatedDriveMinutes;
    private int totalDurationMinutes;
    private double estimatedChargingCost;
    private double estimatedArrivalBatteryPercent;
    private Long activeStationId;
    private Long activeBookingId;
    private AutopilotTripStatus status;
    private String paymentMessage;
    private double walletBalance;
    private AutopilotTelemetryResponse telemetry;
    private List<AutopilotStopResponse> stops;
    private List<AutopilotActionResponse> timeline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
