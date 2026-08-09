package com.vidyut.autopilot.repository;

import com.vidyut.autopilot.entity.AutopilotTrip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AutopilotTripRepository extends JpaRepository<AutopilotTrip, Long> {
    Optional<AutopilotTrip> findByIdAndUserId(Long id, Long userId);
    Optional<AutopilotTrip> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    Optional<AutopilotTrip> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
