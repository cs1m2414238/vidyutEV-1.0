package com.vidyut.booking.service;

import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.dto.BookingStatusUpdateRequest;

import java.util.List;

public interface BookingService {
    BookingResponse createBooking(BookingCreateRequest request, Long userId);
    BookingResponse getBookingById(Long id);
    BookingResponse getBookingById(Long id, Long userId);
    List<BookingResponse> getBookingsByUserId(Long userId);
    BookingResponse updateBookingStatus(Long id, BookingStatusUpdateRequest request);
    void cancelBooking(Long id);
    void cancelBooking(Long id, Long userId);
}
