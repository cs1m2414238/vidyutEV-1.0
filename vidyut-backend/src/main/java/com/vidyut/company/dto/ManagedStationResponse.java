package com.vidyut.company.dto;

import com.vidyut.station.entity.StationStatus;

public record ManagedStationResponse(
        Long id,
        String name,
        String city,
        String address,
        Long hostUserId,
        String relationship,
        StationStatus status,
        int chargerCount,
        int onlineChargers,
        int faultedChargers
) {}
