package com.vidyut.land.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "land_listings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hostUserId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String address;

    private double latitude;
    private double longitude;

    private String connectorType;
    private double powerKw;
    private double pricePerKwh;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LandListingStatus status = LandListingStatus.APPROVED;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
