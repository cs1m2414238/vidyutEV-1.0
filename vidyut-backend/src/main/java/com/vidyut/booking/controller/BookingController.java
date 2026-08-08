package com.vidyut.booking.controller;

import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.service.BookingService;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ev/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;
    private final CurrentUserUtil currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(@Valid @RequestBody BookingCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Booking created successfully",
                bookingService.createBooking(request, currentUser.getCurrentAccountId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getMyBookings() {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getBookingsByUserId(currentUser.getCurrentAccountId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getMyBooking(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getBookingById(id, currentUser.getCurrentAccountId())));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelMyBooking(@PathVariable Long id) {
        bookingService.cancelBooking(id, currentUser.getCurrentAccountId());
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled successfully", null));
    }
}
