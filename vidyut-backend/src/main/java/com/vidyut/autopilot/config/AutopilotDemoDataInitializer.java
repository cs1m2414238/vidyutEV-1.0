package com.vidyut.autopilot.config;

import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vidyut.demo-data.enabled", havingValue = "true")
public class AutopilotDemoDataInitializer implements ApplicationRunner {

    private final ChargingStationRepository stationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> existingNames = stationRepository.findAll().stream()
                .map(ChargingStation::getName)
                .collect(java.util.stream.Collectors.toSet());

        demoStations().stream()
                .filter(station -> !existingNames.contains(station.name()))
                .map(this::toEntity)
                .forEach(stationRepository::save);
    }

    private ChargingStation toEntity(DemoStation demo) {
        ChargingStation station = ChargingStation.builder()
                .name(demo.name())
                .address(demo.address())
                .city(demo.city())
                .latitude(demo.latitude())
                .longitude(demo.longitude())
                .pricePerKwh(demo.pricePerKwh())
                .rating(demo.rating())
                .reviewCount(120)
                .amenities("Cafe, Washroom, Wi-Fi, 24x7 Security")
                .workingHours("Open 24 hours")
                .queueCount(demo.queueCount())
                .occupancyPercent(demo.occupancyPercent())
                .status(StationStatus.ACTIVE)
                .availability(StationAvailability.AVAILABLE)
                .bookingSlotMinutes(30)
                .connectors(new ArrayList<>())
                .build();
        addConnector(station, ConnectorType.CCS2, demo.powerKw(), demo.code() + "-C");
        addConnector(station, ConnectorType.TYPE2, Math.min(22, demo.powerKw()), demo.code() + "-T");
        return station;
    }

    private void addConnector(ChargingStation station, ConnectorType type, double powerKw, String code) {
        station.getConnectors().add(ChargingConnector.builder()
                .station(station)
                .type(type)
                .powerKw(powerKw)
                .available(true)
                .chargerCode(code)
                .status(ChargerStatus.ONLINE)
                .healthScore(96)
                .firmwareVersion("3.5.0")
                .build());
    }

    private List<DemoStation> demoStations() {
        return List.of(
                new DemoStation("Vidyut Kanpur Gateway", "NH-19, Kanpur Dehat", "Kanpur", 26.5220, 80.0350, 13.0, 4.8, 1, 38, 120, "VY-KNP"),
                new DemoStation("Vidyut Etawah Express", "Agra-Lucknow Expressway, Etawah", "Etawah", 26.7829, 79.0277, 12.5, 4.7, 0, 25, 150, "VY-ETW"),
                new DemoStation("GreenCharge Agra", "Fatehabad Road, Agra", "Agra", 27.1767, 78.0081, 14.2, 4.9, 3, 72, 150, "VY-AGR"),
                new DemoStation("VoltPoint Mathura", "Yamuna Expressway, Mathura", "Mathura", 27.4924, 77.6737, 13.4, 4.6, 0, 31, 180, "VY-MTR"),
                new DemoStation("Yamuna Energy Plaza", "Jewar Toll Plaza, Gautam Budh Nagar", "Greater Noida", 28.1580, 77.5540, 12.9, 4.8, 1, 42, 180, "VY-JWR"),
                new DemoStation("Vidyut Greater Noida", "Knowledge Park II, Greater Noida", "Greater Noida", 28.4744, 77.5040, 15.0, 4.9, 0, 28, 240, "VY-GNO"),
                new DemoStation("Delhi Arrival Hub", "Sarai Kale Khan, New Delhi", "Delhi", 28.5890, 77.2500, 16.2, 4.7, 2, 61, 150, "VY-DEL"),
                new DemoStation("Vidyut Jaipur NH48", "Ajmer Road, Jaipur", "Jaipur", 26.8870, 75.7050, 13.8, 4.8, 1, 36, 180, "VY-JAI"),
                new DemoStation("Vidyut Kishangarh Corridor", "NH48, Kishangarh", "Kishangarh", 26.5906, 74.8564, 12.9, 4.7, 0, 24, 180, "VY-KSG"),
                new DemoStation("Vidyut Udaipur Gateway", "NH48, Udaipur", "Udaipur", 24.6500, 73.7100, 14.0, 4.9, 1, 40, 240, "VY-UDR"),
                new DemoStation("Vidyut Ahmedabad Ring", "SG Highway, Ahmedabad", "Ahmedabad", 23.0700, 72.5000, 13.5, 4.8, 2, 52, 240, "VY-AMD"),
                new DemoStation("Vidyut Vadodara Express", "NE1 Junction, Vadodara", "Vadodara", 22.3072, 73.1812, 12.7, 4.6, 0, 30, 180, "VY-BDQ"),
                new DemoStation("Vidyut Surat NH48", "Kadodara, Surat", "Surat", 21.2200, 72.9600, 13.2, 4.8, 1, 45, 240, "VY-STV"),
                new DemoStation("Vidyut Mumbai Arrival", "Thane, Mumbai Metropolitan Region", "Mumbai", 19.2183, 72.9781, 16.0, 4.9, 2, 60, 240, "VY-BOM")
        );
    }

    private record DemoStation(
            String name,
            String address,
            String city,
            double latitude,
            double longitude,
            double pricePerKwh,
            double rating,
            int queueCount,
            double occupancyPercent,
            double powerKw,
            String code
    ) {}
}
