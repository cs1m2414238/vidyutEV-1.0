package com.vidyut.booking.service;

import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.dto.BookingStatusUpdateRequest;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final ChargingStationRepository stationRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    public BookingResponse createBooking(BookingCreateRequest request, Long userId) {
        ChargingStation station = stationRepository.findById(request.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Charging station not found with id: " + request.getStationId()));

        int hours = request.getDurationHours() > 0 ? request.getDurationHours() : 1;
        double estKwh = hours * 7.4;
        double totalAmount = estKwh * station.getPricePerKwh();

        if (request.getVehicleId() != null) {
            vehicleRepository.findByIdAndUserId(request.getVehicleId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this account"));
        }

        Booking booking = Booking.builder()
                .userId(userId)
                .stationId(station.getId())
                .vehicleId(request.getVehicleId())
                .stationName(station.getName())
                .stationAddress(station.getAddress())
                .startTime(request.getStartTime() != null ? request.getStartTime() : LocalDateTime.now())
                .durationHours(hours)
                .totalAmount(totalAmount)
                .kwhDelivered(estKwh)
                .status(BookingStatus.CONFIRMED)
                .build();

        return mapToResponse(bookingRepository.save(booking));
    }

    @Override
    public BookingResponse getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
        return mapToResponse(booking);
    }

    @Override
    public BookingResponse getBookingById(Long id, Long userId) {
        Booking booking = bookingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for this account"));
        return mapToResponse(booking);
    }

    @Override
    public List<BookingResponse> getBookingsByUserId(Long userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public BookingResponse updateBookingStatus(Long id, BookingStatusUpdateRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
        if (request.getStatus() != null) {
            booking.setStatus(request.getStatus());
        }
        return mapToResponse(bookingRepository.save(booking));
    }

    @Override
    public void cancelBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    @Override
    public void cancelBooking(Long id, Long userId) {
        Booking booking = bookingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for this account"));
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    private BookingResponse mapToResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .userId(booking.getUserId())
                .stationId(booking.getStationId())
                .vehicleId(booking.getVehicleId())
                .stationName(booking.getStationName())
                .stationAddress(booking.getStationAddress())
                .startTime(booking.getStartTime())
                .durationHours(booking.getDurationHours())
                .totalAmount(booking.getTotalAmount())
                .kwhDelivered(booking.getKwhDelivered())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
