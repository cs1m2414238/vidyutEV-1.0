package com.vidyut.booking;

import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.service.BookingService;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.dto.StationResponse;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.service.ChargingStationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BookingServiceTest {

    @Autowired
    private ChargingStationService stationService;

    @Autowired
    private BookingService bookingService;

    @Test
    public void testCreateBooking() {
        StationCreateRequest sReq = StationCreateRequest.builder()
                .name("Hazratganj Station")
                .address("Hazratganj, Lucknow")
                .pricePerKwh(15.0)
                .connectorType(ConnectorType.CCS2)
                .powerKw(22.0)
                .build();
        StationResponse station = stationService.createStation(sReq, 1L);

        BookingCreateRequest bReq = BookingCreateRequest.builder()
                .stationId(station.getId())
                .durationHours(2)
                .build();

        BookingResponse booking = bookingService.createBooking(bReq, 101L);
        assertNotNull(booking.getId());
        assertEquals("Hazratganj Station", booking.getStationName());
        assertTrue(booking.getTotalAmount() > 0);
    }
}
