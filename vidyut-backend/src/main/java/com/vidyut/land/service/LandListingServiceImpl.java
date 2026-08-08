package com.vidyut.land.service;

import com.vidyut.land.dto.LandListingCreateRequest;
import com.vidyut.land.dto.LandListingResponse;
import com.vidyut.land.entity.LandListing;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.repository.LandListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LandListingServiceImpl implements LandListingService {

    private final LandListingRepository landListingRepository;

    @Override
    public LandListingResponse createListing(Long hostUserId, LandListingCreateRequest request) {
        LandListing listing = LandListing.builder()
                .hostUserId(hostUserId)
                .title(request.getTitle())
                .address(request.getAddress())
                .latitude(request.getLatitude() != 0 ? request.getLatitude() : 26.8467)
                .longitude(request.getLongitude() != 0 ? request.getLongitude() : 80.9462)
                .connectorType(request.getConnectorType() != null ? request.getConnectorType() : "Type 2")
                .powerKw(request.getPowerKw() > 0 ? request.getPowerKw() : 7.4)
                .pricePerKwh(request.getPricePerKwh())
                .status(LandListingStatus.APPROVED)
                .build();

        return mapToResponse(landListingRepository.save(listing));
    }

    @Override
    public List<LandListingResponse> getAllListings() {
        return landListingRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<LandListingResponse> getListingsByHostUserId(Long hostUserId) {
        return landListingRepository.findByHostUserId(hostUserId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private LandListingResponse mapToResponse(LandListing l) {
        return LandListingResponse.builder()
                .id(l.getId())
                .hostUserId(l.getHostUserId())
                .title(l.getTitle())
                .address(l.getAddress())
                .latitude(l.getLatitude())
                .longitude(l.getLongitude())
                .connectorType(l.getConnectorType())
                .powerKw(l.getPowerKw())
                .pricePerKwh(l.getPricePerKwh())
                .status(l.getStatus())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
