package com.vidyut.station.repository;

import com.vidyut.station.entity.ChargingStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingStationRepository extends JpaRepository<ChargingStation, Long> {
    List<ChargingStation> findByCityContainingIgnoreCase(String city);
    List<ChargingStation> findByHostUserId(Long hostUserId);
    List<ChargingStation> findBySupplierCompanyId(Long supplierCompanyId);
    Optional<ChargingStation> findByIdAndHostUserId(Long id, Long hostUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct s from ChargingStation s left join fetch s.connectors where s.id = :id")
    Optional<ChargingStation> findLockedById(@Param("id") Long id);
}
