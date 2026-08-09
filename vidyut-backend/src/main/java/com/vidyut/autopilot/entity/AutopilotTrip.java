package com.vidyut.autopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "autopilot_trips",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_autopilot_user_idempotency",
                columnNames = {"user_id", "idempotency_key"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotTrip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 1200)
    private String goal;

    @Column(nullable = false, length = 160)
    private String origin;

    @Column(nullable = false, length = 160)
    private String destination;

    @Column(length = 20)
    private String arrivalDeadline;

    @Column(nullable = false, length = 30)
    private String optimizeFor;

    private double startingBatteryPercent;
    private double currentBatteryPercent;
    private double minimumArrivalBatteryPercent;
    private double maximumChargingBudget;
    private double totalDistanceKm;
    private int estimatedDriveMinutes;
    private int totalDurationMinutes;
    private double estimatedChargingCost;
    private double estimatedArrivalBatteryPercent;
    private Long activeStationId;
    private Long activeBookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutopilotTripStatus status;

    @Column(length = 500)
    private String paymentMessage;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
