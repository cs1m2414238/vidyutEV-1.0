package com.vidyut.autopilot.dto;

import com.vidyut.autopilot.entity.AutopilotStopStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotStopResponse {
    private Long id;
    private int sequenceNumber;
    private Long stationId;
    private Long bookingId;
    private String stationName;
    private String stationAddress;
    private String connectorType;
    private double powerKw;
    private double distanceFromOriginKm;
    private double arrivalBatteryPercent;
    private double targetBatteryPercent;
    private int estimatedWaitMinutes;
    private int chargingMinutes;
    private double estimatedCost;
    private String selectionReason;
    private String timingScore;
    private String timingLabel;
    private AutopilotStopStatus status;
}
