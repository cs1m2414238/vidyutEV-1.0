package com.vidyut.station.dto;

import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationUpdateRequest {
    private String name;
    private String address;
    private Double pricePerKwh;
    private StationStatus status;
    private StationAvailability availability;
    private String city;
    private Double latitude;
    private Double longitude;
    private String imageUrl;
    private String photoUrls;
    private String amenities;
    private String workingHours;
    private String weeklySchedule;
    private String holidaySchedule;
    private String chargingInstructions;
    private Boolean autoAvailability;
    private Boolean emergencyDisabled;
    private Integer bookingSlotMinutes;
    private Integer queueCount;
    private Double occupancyPercent;
}
