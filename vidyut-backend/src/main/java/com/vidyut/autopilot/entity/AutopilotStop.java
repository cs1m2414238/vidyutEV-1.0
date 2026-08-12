package com.vidyut.autopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "autopilot_stops")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tripId;

    @Column(nullable = false)
    private int sequenceNumber;

    @Column(nullable = false)
    private Long stationId;

    private Long bookingId;

    @Column(nullable = false)
    private String stationName;

    @Column(nullable = false)
    private String stationAddress;

    @Column(nullable = false, length = 30)
    private String connectorType;

    private double powerKw;
    private double distanceFromOriginKm;
    private double arrivalBatteryPercent;
    private double targetBatteryPercent;
    private int estimatedWaitMinutes;
    private int chargingMinutes;
    private double estimatedCost;

    @Column(length = 1000)
    private String selectionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutopilotStopStatus status;
}
