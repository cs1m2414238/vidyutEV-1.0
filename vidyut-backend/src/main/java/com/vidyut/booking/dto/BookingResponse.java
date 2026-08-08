package com.vidyut.booking.dto;

import com.vidyut.booking.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private Long userId;
    private Long stationId;
    private Long vehicleId;
    private String stationName;
    private String stationAddress;
    private LocalDateTime startTime;
    private int durationHours;
    private double totalAmount;
    private double kwhDelivered;
    private BookingStatus status;
    private LocalDateTime createdAt;
}
