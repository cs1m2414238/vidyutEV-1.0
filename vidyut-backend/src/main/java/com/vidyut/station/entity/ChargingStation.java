package com.vidyut.station.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "charging_stations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    private String city;

    private double latitude;
    private double longitude;

    private double pricePerKwh;
    private double rating;
    private int reviewCount;
    private String imageUrl;

    @Column(length = 2000)
    private String photoUrls;

    @Column(length = 1000)
    private String amenities;

    private String workingHours;

    @Column(length = 1500)
    private String weeklySchedule;

    @Column(length = 1500)
    private String holidaySchedule;

    @Column(length = 1000)
    private String chargingInstructions;

    @Builder.Default
    private boolean autoAvailability = false;

    @Builder.Default
    private boolean emergencyDisabled = false;

    @Builder.Default
    private int bookingSlotMinutes = 60;

    @Builder.Default
    private int queueCount = 0;

    @Builder.Default
    private double occupancyPercent = 0;

    @Builder.Default
    private boolean dynamicPricingEnabled = false;

    private Double timeBasedPricePerHour;
    private Double peakPricePerKwh;
    private String peakHours;
    private Double studentDiscountPercent;
    private Double corporatePricePerKwh;
    private String couponCode;
    private Double couponDiscountPercent;

    @Builder.Default
    private boolean outletPartner = false;

    private String outletInstitutionName;

    @Column(length = 1000)
    private String outletEmailDomains;

    @Builder.Default
    private boolean outletIdVerificationRequired = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StationStatus status = StationStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StationAvailability availability = StationAvailability.AVAILABLE;

    private Long hostUserId;

    private Long supplierCompanyId;

    @Column(unique = true)
    private Long sourceInstallationRequestId;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChargingConnector> connectors = new ArrayList<>();
}
