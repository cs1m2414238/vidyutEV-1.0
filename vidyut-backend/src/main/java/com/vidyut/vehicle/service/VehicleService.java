package com.vidyut.vehicle.service;

import com.vidyut.vehicle.dto.VehicleCreateRequest;
import com.vidyut.vehicle.dto.VehicleResponse;

import java.util.List;

public interface VehicleService {
    VehicleResponse addVehicle(Long userId, VehicleCreateRequest request);
    List<VehicleResponse> getVehiclesByUserId(Long userId);
    void deleteVehicle(Long id);
    void deleteVehicle(Long id, Long userId);
}
