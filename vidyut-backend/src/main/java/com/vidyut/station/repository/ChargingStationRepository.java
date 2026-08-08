package com.vidyut.station.repository;

import com.vidyut.station.entity.ChargingStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingStationRepository extends JpaRepository<ChargingStation, Long> {
    List<ChargingStation> findByCityContainingIgnoreCase(String city);
    List<ChargingStation> findByHostUserId(Long hostUserId);
    Optional<ChargingStation> findByIdAndHostUserId(Long id, Long hostUserId);
}
