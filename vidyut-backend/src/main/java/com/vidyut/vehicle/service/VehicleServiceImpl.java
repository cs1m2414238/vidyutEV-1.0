package com.vidyut.vehicle.service;

import com.vidyut.common.exception.DuplicateResourceException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.vehicle.dto.VehicleCreateRequest;
import com.vidyut.vehicle.dto.VehicleResponse;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.repository.VehicleAutoRechargeRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleAutoRechargeRuleRepository autoRechargeRuleRepository;

    @Override
    public VehicleResponse addVehicle(Long userId, VehicleCreateRequest request) {
        if (vehicleRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new DuplicateResourceException("Vehicle already registered with number: " + request.getRegistrationNumber());
        }

        Vehicle vehicle = Vehicle.builder()
                .userId(userId)
                .makeAndModel(request.getMakeAndModel())
                .registrationNumber(request.getRegistrationNumber())
                .batteryCapacity(request.getBatteryCapacity() != null ? request.getBatteryCapacity() : "40.5 kWh")
                .connectorType(request.getConnectorType() != null ? request.getConnectorType() : "CCS2 / Type 2")
                .build();

        return mapToResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public List<VehicleResponse> getVehiclesByUserId(Long userId) {
        return vehicleRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
        autoRechargeRuleRepository.deleteByUserIdAndVehicle_Id(vehicle.getUserId(), id);
        vehicleRepository.delete(vehicle);
    }

    @Override
    @Transactional
    public void deleteVehicle(Long id, Long userId) {
        Vehicle vehicle = vehicleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this account"));
        autoRechargeRuleRepository.deleteByUserIdAndVehicle_Id(userId, id);
        vehicleRepository.delete(vehicle);
    }

    private VehicleResponse mapToResponse(Vehicle v) {
        return VehicleResponse.builder()
                .id(v.getId())
                .userId(v.getUserId())
                .makeAndModel(v.getMakeAndModel())
                .registrationNumber(v.getRegistrationNumber())
                .batteryCapacity(v.getBatteryCapacity())
                .connectorType(v.getConnectorType())
                .build();
    }
}
