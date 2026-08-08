package com.vidyut.station.dto;

import com.vidyut.station.entity.ConnectorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationCreateRequest {

    @NotBlank(message = "Station name is required")
    private String name;

    @NotBlank(message = "Address is required")
    private String address;

    private String city;
    private double latitude;
    private double longitude;

    @NotNull(message = "Price per kWh is required")
    private Double pricePerKwh;

    private ConnectorType connectorType;
    private double powerKw;
    private String imageUrl;
    private String photoUrls;
    private String amenities;
    private String workingHours;
    private String weeklySchedule;
    private String holidaySchedule;
    private String chargingInstructions;
    private boolean autoAvailability;
    private int bookingSlotMinutes;
}
