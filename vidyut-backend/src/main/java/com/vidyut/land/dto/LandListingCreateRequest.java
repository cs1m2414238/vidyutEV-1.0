package com.vidyut.land.dto;

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
public class LandListingCreateRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Address is required")
    private String address;

    private double latitude;
    private double longitude;

    private String connectorType;
    private double powerKw;

    @NotNull(message = "Price per kWh is required")
    private Double pricePerKwh;
}
