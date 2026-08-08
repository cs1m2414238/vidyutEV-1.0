package com.vidyut.booking.repository;

import com.vidyut.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);
    Optional<Booking> findByIdAndUserId(Long id, Long userId);
    List<Booking> findByStationId(Long stationId);
    List<Booking> findByStationIdInOrderByStartTimeDesc(List<Long> stationIds);
}
