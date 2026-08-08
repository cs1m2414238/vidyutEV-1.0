package com.vidyut.land.dto;

import com.vidyut.land.entity.LandListingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandListingResponse {
    private Long id;
    private Long hostUserId;
    private String title;
    private String address;
    private double latitude;
    private double longitude;
    private String connectorType;
    private double powerKw;
    private double pricePerKwh;
    private LandListingStatus status;
    private LocalDateTime createdAt;
}
