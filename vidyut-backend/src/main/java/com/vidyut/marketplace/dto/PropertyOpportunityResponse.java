package com.vidyut.marketplace.dto;

public record PropertyOpportunityResponse(
        Long id, String title, String address, String city, String state, String pincode,
        double latitude, double longitude, String propertyType, int parkingBays,
        String powerPhase, double availableLoadKw, String operatingHours,
        String ownershipType, String preferredConnectorType, double preferredPowerKw,
        String photoUrls, String matchedBy, Double distanceKm
) {}
