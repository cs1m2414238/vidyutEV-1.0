package com.vidyut.host.config;

import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.entity.EvUserProfile;
import com.vidyut.account.entity.HostProfile;
import com.vidyut.account.entity.HostVerificationStatus;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.host.entity.HostReview;
import com.vidyut.host.repository.HostReviewRepository;
import com.vidyut.session.entity.ChargingSession;
import com.vidyut.session.entity.ChargingSessionStatus;
import com.vidyut.session.repository.ChargingSessionRepository;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.entity.StationOwnershipType;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.entity.VehicleConnectionStatus;
import com.vidyut.vehicle.entity.VehicleTelemetrySource;
import com.vidyut.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vidyut.demo-data.enabled", havingValue = "true")
public class PrinceHostDemoInitializer implements ApplicationRunner {

    private static final String HOST_ID = "HOST-PRINCE-01";

    private final AccountRepository accountRepository;
    private final HostProfileRepository hostProfileRepository;
    private final EvUserProfileRepository evUserProfileRepository;
    private final ChargingStationRepository stationRepository;
    private final BookingRepository bookingRepository;
    private final HostReviewRepository reviewRepository;
    private final VehicleRepository vehicleRepository;
    private final ChargingSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${vidyut.demo-data.prince-email:prince@vidyut.demo}")
    private String princeEmail;

    @Value("${vidyut.demo-data.prince-password:PrinceDemo123!}")
    private String princePassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Account prince = ensureAccount(princeEmail, princePassword, AccountRole.ROLE_HOST);
        ensurePrinceProfile(prince);
        Map<String, ChargingStation> corridor = seedCorridor(prince.getId());
        Account driver = ensureAccount("corridor.driver@vidyut.demo", "CorridorDemo123!", AccountRole.ROLE_EV_USER);
        ensureDriverProfile(driver);
        seedCustomerSignals(prince.getId(), driver.getId(), corridor);
        seedLiveSessions(driver.getId(), corridor);
    }

    private Account ensureAccount(String email, String password, AccountRole role) {
        Account account = accountRepository.findByEmailIgnoreCase(email).orElseGet(() -> accountRepository.save(
                Account.builder()
                        .email(email.toLowerCase())
                        .passwordHash(passwordEncoder.encode(password))
                        .accountType(AccountType.INDIVIDUAL)
                        .roles(new HashSet<>(Set.of(role)))
                        .enabled(true)
                        .emailVerified(true)
                        .build()));
        if (account.getAccountType() == AccountType.INDIVIDUAL && !account.getRoles().contains(role)) {
            account.getRoles().add(role);
        }
        account.setEnabled(true);
        account.setEmailVerified(true);
        return accountRepository.save(account);
    }

    private void ensurePrinceProfile(Account account) {
        HostProfile profile = hostProfileRepository.findById(account.getId()).orElseGet(() -> HostProfile.builder()
                .account(account)
                .displayName("Prince")
                .build());
        profile.setDisplayName("Prince");
        profile.setPhone("9000000101");
        profile.setVerified(true);
        profile.setVerificationStatus(HostVerificationStatus.VERIFIED);
        profile.setBio(HOST_ID + " · Property Host for a multi-operator EV charging portfolio");
        profile.setAddress("Lucknow–Kanpur–Jhansi–Bhopal corridor · Agra solar property");
        profile.setBankVerified(true);
        profile.setBankAccountHolder("Prince");
        profile.setBankName("Vidyut Demo Bank");
        profile.setBankAccountLast4("0101");
        profile.setIfscCode("VIDY0000101");
        profile.setAutoAvailability(true);
        profile.setReputationScore(4.6);
        hostProfileRepository.save(profile);
    }

    private void ensureDriverProfile(Account account) {
        if (evUserProfileRepository.findById(account.getId()).isEmpty()) {
            evUserProfileRepository.save(EvUserProfile.builder()
                    .account(account)
                    .fullName("Corridor Demo Driver")
                    .phone("9000000001")
                    .build());
        }
    }

    private Map<String, ChargingStation> seedCorridor(Long hostId) {
        Map<String, ChargingStation> existing = stationRepository.findAll().stream()
                .filter(station -> station.getDemoSeedKey() != null)
                .collect(Collectors.toMap(ChargingStation::getDemoSeedKey, Function.identity(), (first, ignored) -> first));
        List<StationSeed> seeds = List.of(
                new StationSeed("host-prince-lucknow", "Prince Lucknow Hub", "Faizabad Road, Lucknow, Uttar Pradesh", "Lucknow", 26.8756, 81.0021, 17.8,
                        "Cafe, Restroom, CCTV, City parking", "TATA Power Demo", null,
                        List.of(new ConnectorSeed("TATA-LKO-01", ConnectorType.CCS2, 120, 97, null),
                                new ConnectorSeed("TATA-LKO-02", ConnectorType.CCS2, 120, 96, null),
                                new ConnectorSeed("TATA-LKO-03", ConnectorType.CCS2, 150, 95, null),
                                new ConnectorSeed("TATA-LKO-04", ConnectorType.TYPE2, 22, 98, null))),
                new StationSeed("host-prince-kanpur", "Prince Kanpur Highway Hub", "NH-19 corridor, Kanpur, Uttar Pradesh", "Kanpur", 26.4499, 80.3319, 18.0,
                        "Cafe, Restroom, CCTV, Highway parking", "TATA Power Demo", null,
                        List.of(new ConnectorSeed("TATA-KNP-01", ConnectorType.CCS2, 120, 96, null),
                                new ConnectorSeed("TATA-KNP-02", ConnectorType.CCS2, 120, 94, null),
                                new ConnectorSeed("TATA-KNP-03", ConnectorType.CCS2, 150, 58, "COOLING_SYSTEM_TEMP_HIGH · DEMO FAULT READY"))),
                new StationSeed("host-prince-sagar", "Prince Bhopal City Hub", "Hoshangabad Road, Bhopal, Madhya Pradesh", "Bhopal", 23.1870, 77.4330, 16.6,
                        "Food court, Restroom, CCTV, 24x7 security", "ChargeZone Demo", null,
                        List.of(new ConnectorSeed("CZ-BPL-01", ConnectorType.CCS2, 180, 97, null),
                                new ConnectorSeed("CZ-BPL-02", ConnectorType.CCS2, 180, 96, null),
                                new ConnectorSeed("CZ-BPL-03", ConnectorType.CCS2, 120, 95, null),
                                new ConnectorSeed("CZ-BPL-04", ConnectorType.TYPE2, 22, 98, null),
                                new ConnectorSeed("CZ-BPL-05", ConnectorType.TYPE2, 22, 94, null))),
                new StationSeed("host-prince-jhansi", "Prince Jhansi Rest Stop", "NH-44 transit corridor, Jhansi, Uttar Pradesh", "Jhansi", 25.4484, 78.5685, 15.2,
                        "Restroom, Food court, CCTV, 24x7 security", "Statiq Demo", null,
                        List.of(new ConnectorSeed("STATIQ-JHS-01", ConnectorType.CCS2, 150, 93, null),
                                new ConnectorSeed("STATIQ-JHS-02", ConnectorType.TYPE2, 22, 55, "INTERMITTENT_HEARTBEAT · DEMO SERVICE SIGNAL"))),
                new StationSeed("host-prince-agra", "Prince Agra Solar Hub", "Agra Ring Road, Agra, Uttar Pradesh", "Agra", 27.1767, 78.0081, 17.2,
                        "70 kW solar canopy, Cafe, Restroom, CCTV, Safe seating", "TATA Power Demo", "SunRoute RESCO Demo",
                        List.of(new ConnectorSeed("TATA-AGR-01", ConnectorType.CCS2, 120, 98, null),
                                new ConnectorSeed("TATA-AGR-02", ConnectorType.CCS2, 120, 97, null),
                                new ConnectorSeed("TATA-AGR-03", ConnectorType.CCS2, 120, 96, null),
                                new ConnectorSeed("TATA-AGR-04", ConnectorType.TYPE2, 22, 98, null),
                                new ConnectorSeed("TATA-AGR-05", ConnectorType.TYPE2, 22, 97, null),
                                new ConnectorSeed("TATA-AGR-06", ConnectorType.TYPE2, 22, 96, null)))
        );

        for (StationSeed seed : seeds) {
            ChargingStation station = existing.get(seed.key());
            if (station == null) {
                station = ChargingStation.builder()
                        .name(seed.name()).address(seed.address()).city(seed.city())
                        .latitude(seed.latitude()).longitude(seed.longitude()).pricePerKwh(seed.pricePerKwh())
                        .rating(4.6).reviewCount(84).amenities(seed.amenities())
                        .workingHours("Open 24 hours").weeklySchedule("MON-SUN 00:00-24:00")
                        .chargingInstructions("DEMO DATA · Corridor reservations supported by Vidyut Autopilot")
                        .autoAvailability(true).bookingSlotMinutes(30).queueCount(0).occupancyPercent(48)
                        .status(StationStatus.ACTIVE).availability(StationAvailability.AVAILABLE)
                        .hostUserId(hostId).propertyOwnerAccountId(hostId)
                        .ownershipType(StationOwnershipType.HOST_PARTNERED)
                        .demoData(true).demoSeedKey(seed.key())
                        .connectors(new ArrayList<>()).build();
                applyOperatingParties(station, seed);
                syncConnectors(station, seed.connectors());
                station = stationRepository.save(station);
            } else {
                station.setHostUserId(hostId);
                station.setPropertyOwnerAccountId(hostId);
                station.setOwnershipType(StationOwnershipType.HOST_PARTNERED);
                station.setDemoData(true);
                station.setName(seed.name());
                station.setAddress(seed.address());
                station.setCity(seed.city());
                station.setLatitude(seed.latitude());
                station.setLongitude(seed.longitude());
                station.setPricePerKwh(seed.pricePerKwh());
                station.setRating(4.8);
                station.setQueueCount(0);
                station.setOccupancyPercent(seed.solarProvider() != null ? 24 : 36);
                station.setAmenities(seed.amenities());
                station.setChargingInstructions("DEMO DATA · Corridor reservations supported by Vidyut Autopilot");
                applyOperatingParties(station, seed);
                syncConnectors(station, seed.connectors());
                station = stationRepository.save(station);
            }
            existing.put(seed.key(), station);
        }
        return existing;
    }

    private void applyOperatingParties(ChargingStation station, StationSeed seed) {
        station.setPropertyOwnerName("Prince");
        station.setOperatorCompanyName(seed.operatorCompany());
        station.setEquipmentOwnerName(seed.operatorCompany());
        station.setOperatingModel("HOST_PROPERTY_CPO_EQUIPMENT");
        station.setSolarProviderName(seed.solarProvider());
    }

    private void syncConnectors(ChargingStation station, List<ConnectorSeed> seeds) {
        if (station.getConnectors() == null) station.setConnectors(new ArrayList<>());
        station.getConnectors().sort(java.util.Comparator.comparing(connector ->
                connector.getId() == null ? Long.MAX_VALUE : connector.getId()));
        while (station.getConnectors().size() > seeds.size()) {
            station.getConnectors().remove(station.getConnectors().size() - 1);
        }
        for (int index = 0; index < seeds.size(); index++) {
            ConnectorSeed seed = seeds.get(index);
            ChargingConnector connector;
            if (index < station.getConnectors().size()) {
                connector = station.getConnectors().get(index);
            } else {
                connector = ChargingConnector.builder()
                        .station(station).available(true).status(ChargerStatus.ONLINE)
                        .maintenanceMode(false).build();
                station.getConnectors().add(connector);
            }
            connector.setStation(station);
            connector.setChargerCode(seed.code());
            connector.setType(seed.type());
            connector.setPowerKw(seed.powerKw());
            connector.setHealthScore(seed.healthScore());
            connector.setFaultCode(seed.customerSignal());
            connector.setFirmwareVersion("prince-multi-operator-demo-2.0");
        }
    }

    private void seedCustomerSignals(Long hostId, Long driverId, Map<String, ChargingStation> stations) {
        List<ChargingStation> routeStations = List.of(
                stations.get("host-prince-lucknow"), stations.get("host-prince-kanpur"),
                stations.get("host-prince-jhansi"), stations.get("host-prince-sagar"),
                stations.get("host-prince-agra"));
        for (int index = 0; index < 15; index++) {
            ChargingStation station = routeStations.get(index % routeStations.size());
            String key = "PRINCE-DEMO-SESSION-" + index;
            int daysAgo = index % 7;
            LocalDateTime start = daysAgo == 0
                    ? LocalDateTime.now().minusHours(1 + index % 3).withMinute(0).withSecond(0).withNano(0)
                    : LocalDateTime.now().minusDays(daysAgo).withHour(17 + index % 4).withMinute(0).withSecond(0).withNano(0);
            Booking booking = bookingRepository.findByUserIdAndIdempotencyKey(driverId, key)
                    .orElseGet(() -> Booking.builder()
                            .userId(driverId).vehicleId(null).idempotencyKey(key).build());
            booking.setStationId(station.getId());
            booking.setStationName(station.getName());
            booking.setStationAddress(station.getAddress());
            booking.setStartTime(start);
            booking.setEndTime(start.plusMinutes(38));
            booking.setDurationHours(1);
            booking.setDurationMinutes(38);
            booking.setTotalAmount(118 + (index % 5) * 19);
            booking.setKwhDelivered(10 + (index % 4) * 2.5);
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setSeen(true);
            bookingRepository.save(booking);
        }
        ChargingStation kanpur = stations.get("host-prince-kanpur");
        Set<Long> reviewedBookings = reviewRepository.findByHostAccountIdOrderByCreatedAtDesc(hostId).stream()
                .map(HostReview::getBookingId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        bookingRepository.findByStationId(kanpur.getId()).stream().limit(4).forEach(booking -> {
            if (reviewedBookings.contains(booking.getId())) return;
            reviewRepository.save(HostReview.builder()
                    .hostAccountId(hostId).stationId(kanpur.getId()).bookingId(booking.getId())
                    .customerAccountId(driverId).customerName("Corridor Demo Driver")
                    .rating(2).comment("CCS2 connector 2 needed repeated starts and felt loose. DEMO DATA.")
                    .createdAt(LocalDateTime.now().minusDays(1)).build());
        });
    }

    private void seedLiveSessions(Long driverId, Map<String, ChargingStation> stations) {
        List<LiveSessionSeed> seeds = List.of(
                new LiveSessionSeed("host-prince-lucknow", "TATA-LKO-01", "Tata Nexon EV Demo",
                        "UP32LIV001", 45.0, 60.0, 38, 66, 80, 18, 14, 19.4, 46.0),
                new LiveSessionSeed("host-prince-kanpur", "TATA-KNP-01", "BMW i4 Demo",
                        "UP32LIV002", 83.9, 150.0, 24, 41, 75, 11, 22, 14.8, 108.0),
                new LiveSessionSeed("host-prince-sagar", "CZ-BPL-01", "Mahindra XUV400 Demo",
                        "UP32LIV003", 39.4, 50.0, 43, 62, 90, 23, 19, 17.2, 42.0));

        for (int index = 0; index < seeds.size(); index++) {
            LiveSessionSeed seed = seeds.get(index);
            ChargingStation station = stations.get(seed.stationKey());
            if (station == null) continue;
            ChargingConnector connector = station.getConnectors().stream()
                    .filter(item -> seed.connectorCode().equals(item.getChargerCode()))
                    .findFirst().orElse(null);
            if (connector == null) continue;

            Vehicle vehicle = vehicleRepository.findAll().stream()
                    .filter(item -> seed.registration().equalsIgnoreCase(item.getRegistrationNumber()))
                    .findFirst().orElseGet(() -> Vehicle.builder()
                            .userId(driverId)
                            .makeAndModel(seed.vehicleName())
                            .registrationNumber(seed.registration())
                            .build());
            vehicle.setUserId(driverId);
            vehicle.setMakeAndModel(seed.vehicleName());
            vehicle.setBatteryCapacity(String.format(java.util.Locale.ROOT, "%.1f kWh", seed.batteryCapacityKwh()));
            vehicle.setConnectorType(ConnectorType.CCS2.name());
            vehicle.setSupportedConnectors(new java.util.LinkedHashSet<>(Set.of(ConnectorType.CCS2, ConnectorType.TYPE2)));
            vehicle.setEfficiencyWhPerKm(155.0);
            vehicle.setMaxAcChargePowerKw(11.0);
            vehicle.setMaxDcChargePowerKw(seed.vehicleMaxDcKw());
            vehicle.setChargingEfficiency(0.90);
            vehicle.setConnectionStatus(VehicleConnectionStatus.CONNECTED);
            vehicle.setBatteryPercent(seed.currentSoc());
            vehicle.setRemainingRangeKm(Math.round(seed.currentSoc() * seed.batteryCapacityKwh() * 10.0 / 1.55) / 100.0);
            vehicle.setCharging(true);
            vehicle.setLastChargingStation(station.getName());
            vehicle.setLastChargingAddress(station.getAddress());
            vehicle.setLastChargedAt(LocalDateTime.now());
            vehicle.setTelemetrySource(VehicleTelemetrySource.CHARGING_SESSION);
            vehicle.setTelemetryUpdatedAt(LocalDateTime.now());
            vehicle = vehicleRepository.save(vehicle);

            LocalDateTime startedAt = LocalDateTime.now().minusMinutes(seed.elapsedMinutes());
            LocalDateTime completionAt = LocalDateTime.now().plusMinutes(seed.remainingMinutes());
            String idempotencyKey = "PRINCE-LIVE-SESSION-" + index;
            Booking booking = bookingRepository.findByUserIdAndIdempotencyKey(driverId, idempotencyKey)
                    .orElseGet(() -> Booking.builder().userId(driverId).idempotencyKey(idempotencyKey).build());
            booking.setVehicleId(vehicle.getId());
            booking.setStationId(station.getId());
            booking.setStationName(station.getName());
            booking.setStationAddress(station.getAddress());
            booking.setStartTime(startedAt);
            booking.setEndTime(completionAt);
            booking.setDurationHours(1);
            booking.setDurationMinutes(seed.elapsedMinutes() + seed.remainingMinutes());
            booking.setKwhDelivered(Math.max(seed.energyKwh(), seed.batteryCapacityKwh()));
            booking.setTotalAmount(round(seed.energyKwh() * station.getPricePerKwh()));
            booking.setStatus(BookingStatus.IN_PROGRESS);
            booking.setSeen(false);
            booking = bookingRepository.save(booking);
            Long savedBookingId = booking.getId();

            ChargingSession session = sessionRepository.findByBookingIdAndUserId(savedBookingId, driverId)
                    .orElseGet(() -> ChargingSession.builder()
                            .userId(driverId).bookingId(savedBookingId).stationId(station.getId()).build());
            session.setConnectorId(connector.getId());
            session.setVehicleId(vehicle.getId());
            session.setStatus(ChargingSessionStatus.ACTIVE);
            session.setPaymentStatus("DUE");
            session.setPowerKw(seed.powerKw());
            session.setEnergyKwh(seed.energyKwh());
            session.setCost(round(seed.energyKwh() * station.getPricePerKwh()));
            session.setStartBatteryPercent(seed.startSoc());
            session.setCurrentBatteryPercent(seed.currentSoc());
            session.setTargetBatteryPercent(seed.targetSoc());
            session.setStartedAt(startedAt);
            session.setEstimatedCompletionAt(completionAt);
            session.setCompletedAt(null);
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);

            connector.setStatus(ChargerStatus.CHARGING);
            connector.setAvailable(false);
            connector.setMaintenanceMode(false);
            connector.setCurrentPowerKw(seed.powerKw());
            connector.setSessionEnergyKwh(seed.energyKwh());
            connector.setSessionStartedAt(startedAt);
            connector.setLastHeartbeat(LocalDateTime.now());
            connector.setFaultCode(null);
            stationRepository.save(station);
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record StationSeed(String key, String name, String address, String city, double latitude,
                               double longitude, double pricePerKwh, String amenities, String operatorCompany,
                               String solarProvider,
                               List<ConnectorSeed> connectors) {}

    private record ConnectorSeed(String code, ConnectorType type, double powerKw, int healthScore,
                                 String customerSignal) {}

    private record LiveSessionSeed(String stationKey, String connectorCode, String vehicleName,
                                   String registration, double batteryCapacityKwh, double vehicleMaxDcKw,
                                   int startSoc, int currentSoc, int targetSoc, int elapsedMinutes,
                                   int remainingMinutes, double energyKwh, double powerKw) {}
}
