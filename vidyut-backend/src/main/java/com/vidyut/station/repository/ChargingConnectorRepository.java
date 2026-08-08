package com.vidyut.station.repository;

import com.vidyut.station.entity.ChargingConnector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingConnectorRepository extends JpaRepository<ChargingConnector, Long> {
    List<ChargingConnector> findByStation_HostUserId(Long ownerAccountId);
    Optional<ChargingConnector> findByIdAndStation_HostUserId(Long id, Long ownerAccountId);
    boolean existsByChargerCodeIgnoreCase(String chargerCode);
}
