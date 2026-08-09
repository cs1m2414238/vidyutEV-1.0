package com.vidyut.routing;

import com.vidyut.routing.dto.RoutePlanRequest;
import com.vidyut.routing.service.RoutingService;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.service.ChargingStationService;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RoutingServiceTest {
    @Autowired private RoutingService routingService;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ChargingStationService stationService;

    @Test
    void outOfRangeTripRecommendsOnlyCompatibleAvailableStations() {
        long userId = 9101L;
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder().userId(userId).makeAndModel("Nexon EV")
                .registrationNumber("UP32RT9101").batteryCapacity("30 kWh").connectorType("CCS2")
                .batteryPercent(30).remainingRangeKm(55.0).build());
        stationService.createStation(StationCreateRequest.builder().name("CCS Route Stop")
                .address("Amausi, Lucknow").city("Lucknow").latitude(26.7606).longitude(80.8893)
                .pricePerKwh(13.0).connectorType(ConnectorType.CCS2).powerKw(60).bookingSlotMinutes(30).build(), 77L);

        var plan = routingService.planRoute(RoutePlanRequest.builder().origin("Lucknow")
                .destination("Kanpur").vehicleId(vehicle.getId()).currentBatteryPercent(30).build(), userId);

        assertThat(plan.isDestinationWithinRange()).isFalse();
        assertThat(plan.getRecommendedChargingStops()).isNotEmpty();
        assertThat(plan.getRecommendedChargingStops()).allSatisfy(stop -> {
            assertThat(stop.isConnectorMatched()).isTrue();
            assertThat(stop.getAvailableSlots()).isPositive();
        });
    }
}
