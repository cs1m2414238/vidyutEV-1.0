package com.vidyut.company.service;

import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.common.exception.DuplicateResourceException;
import com.vidyut.common.exception.ForbiddenException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.company.dto.*;
import com.vidyut.company.entity.Company;
import com.vidyut.company.entity.CompanyActivityLog;
import com.vidyut.company.entity.CompanyEmployee;
import com.vidyut.company.entity.CompanyMaintenanceTicket;
import com.vidyut.company.entity.MaintenanceTicketStatus;
import com.vidyut.company.repository.CompanyEmployeeRepository;
import com.vidyut.company.repository.CompanyActivityLogRepository;
import com.vidyut.company.repository.CompanyMaintenanceTicketRepository;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.payment.entity.Payment;
import com.vidyut.payment.entity.PaymentStatus;
import com.vidyut.payment.entity.Payout;
import com.vidyut.payment.repository.PaymentRepository;
import com.vidyut.payment.repository.PayoutRepository;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.dto.StationResponse;
import com.vidyut.station.dto.StationUpdateRequest;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.station.service.ChargingStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyOperationsService {
    private final CompanyRepository companyRepository;
    private final CompanyEmployeeRepository employeeRepository;
    private final CompanyActivityLogRepository activityLogRepository;
    private final CompanyMaintenanceTicketRepository maintenanceTicketRepository;
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final ChargingStationService stationService;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PayoutRepository payoutRepository;
    private final LandListingRepository landListingRepository;
    private final CompanyVerificationService verificationService;

    public List<StationResponse> getStations(Long accountId) {
        requireCompany(accountId);
        return stationService.getStationsByOwner(accountId);
    }

    @Transactional
    public StationResponse createStation(Long accountId, StationCreateRequest request) {
        Company company = requireVerifiedCompany(accountId);
        StationResponse station = stationService.createStation(request, accountId);
        recordActivity(company, accountId, "CREATE", "STATION", station.getId(), "Created station " + station.getName());
        return station;
    }

    @Transactional
    public StationResponse updateStation(Long accountId, Long id, StationUpdateRequest request) {
        Company company = requireVerifiedCompany(accountId);
        StationResponse station = stationService.updateStation(id, accountId, request);
        recordActivity(company, accountId, "UPDATE", "STATION", id, "Updated station " + station.getName());
        return station;
    }

    @Transactional
    public void deleteStation(Long accountId, Long id) {
        Company company = requireVerifiedCompany(accountId);
        String stationName = ownedStation(accountId, id).getName();
        stationService.deleteStation(id, accountId);
        recordActivity(company, accountId, "DELETE", "STATION", id, "Deleted station " + stationName);
    }

    public List<ChargerResponse> getChargers(Long accountId) {
        requireCompany(accountId);
        return connectorRepository.findByStation_HostUserId(accountId).stream().map(this::mapCharger).toList();
    }

    @Transactional
    public ChargerResponse createCharger(Long accountId, ChargerRequest request) {
        Company company = requireVerifiedCompany(accountId);
        ChargingStation station = ownedStation(accountId, request.getStationId());
        if (connectorRepository.existsByChargerCodeIgnoreCase(request.getChargerCode())) {
            throw new DuplicateResourceException("Charger code already exists: " + request.getChargerCode());
        }
        ChargingConnector connector = applyCharger(ChargingConnector.builder()
                .station(station)
                .available(true)
                .build(), request);
        station.getConnectors().add(connector);
        ChargerResponse response = mapCharger(connectorRepository.save(connector));
        recordActivity(company, accountId, "CREATE", "CHARGER", response.getId(), "Provisioned charger " + response.getChargerCode());
        return response;
    }

    @Transactional
    public ChargerResponse updateCharger(Long accountId, Long id, ChargerRequest request) {
        Company company = requireVerifiedCompany(accountId);
        ChargingConnector connector = ownedCharger(accountId, id);
        if (!connector.getStation().getId().equals(request.getStationId())) {
            connector.setStation(ownedStation(accountId, request.getStationId()));
        }
        ChargerResponse response = mapCharger(connectorRepository.save(applyCharger(connector, request)));
        recordActivity(company, accountId, "UPDATE", "CHARGER", id, "Updated charger " + response.getChargerCode());
        return response;
    }

    @Transactional
    public void deleteCharger(Long accountId, Long id) {
        Company company = requireVerifiedCompany(accountId);
        ChargingConnector charger = ownedCharger(accountId, id);
        String chargerCode = charger.getChargerCode();
        connectorRepository.delete(charger);
        recordActivity(company, accountId, "DELETE", "CHARGER", id, "Deleted charger " + chargerCode);
    }

    public List<BookingResponse> getBookings(Long accountId) {
        requireCompany(accountId);
        return ownedBookings(accountId).stream().map(this::mapBooking).toList();
    }

    @Transactional
    public BookingResponse updateBookingStatus(Long accountId, Long bookingId, BookingStatus status) {
        Company company = requireVerifiedCompany(accountId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        ownedStation(accountId, booking.getStationId());
        booking.setStatus(status);
        BookingResponse response = mapBooking(bookingRepository.save(booking));
        recordActivity(company, accountId, "STATUS_CHANGE", "BOOKING", bookingId, "Changed booking status to " + status);
        return response;
    }

    @Transactional
    public StationResponse updatePricing(Long accountId, Long stationId, PricingRequest request) {
        Company company = requireVerifiedCompany(accountId);
        ChargingStation station = ownedStation(accountId, stationId);
        station.setPricePerKwh(request.getPricePerKwh());
        station.setTimeBasedPricePerHour(request.getTimeBasedPricePerHour());
        station.setPeakPricePerKwh(request.getPeakPricePerKwh());
        station.setPeakHours(request.getPeakHours());
        station.setStudentDiscountPercent(request.getStudentDiscountPercent());
        station.setCorporatePricePerKwh(request.getCorporatePricePerKwh());
        station.setDynamicPricingEnabled(request.isDynamicPricingEnabled());
        station.setCouponCode(request.getCouponCode());
        station.setCouponDiscountPercent(request.getCouponDiscountPercent());
        stationRepository.save(station);
        StationResponse response = stationService.getStationById(stationId);
        recordActivity(company, accountId, "UPDATE", "PRICING", stationId, "Updated pricing for " + response.getName());
        return response;
    }

    public Map<String, Object> dashboard(Long accountId) {
        requireCompany(accountId);
        List<ChargingStation> stations = stationRepository.findByHostUserId(accountId);
        List<ChargingConnector> chargers = connectorRepository.findByStation_HostUserId(accountId);
        List<Booking> bookings = bookingsForStations(stations);
        List<Payment> payments = paymentsForBookings(bookings);
        double revenue = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount).sum();
        if (revenue == 0) revenue = bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.COMPLETED)
                .mapToDouble(Booking::getTotalAmount).sum();
        long online = chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.ONLINE).count();
        long busy = chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.CHARGING).count();
        long faults = chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.FAULT || charger.getStatus() == ChargerStatus.OFFLINE).count();
        double utilization = chargers.isEmpty() ? 0 : Math.round((busy * 1000.0 / chargers.size())) / 10.0;
        double energy = bookings.stream().mapToDouble(Booking::getKwhDelivered).sum();
        int queue = stations.stream().mapToInt(ChargingStation::getQueueCount).sum();
        return linkedMap(
                "totalStations", stations.size(),
                "totalChargers", chargers.size(),
                "onlineChargers", online,
                "busyChargers", busy,
                "faults", faults,
                "utilizationRate", utilization,
                "activeSessions", bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.IN_PROGRESS).count(),
                "queueCount", queue,
                "energyDeliveredKwh", round(energy),
                "revenue", round(revenue),
                "occupancyPercent", round(stations.stream().mapToDouble(ChargingStation::getOccupancyPercent).average().orElse(0)),
                "alerts", alerts(chargers)
        );
    }

    public Map<String, Object> analytics(Long accountId) {
        Map<String, Object> dashboard = dashboard(accountId);
        List<ChargingStation> stations = stationRepository.findByHostUserId(accountId);
        List<Booking> bookings = bookingsForStations(stations);
        Map<String, Double> stationRevenue = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.COMPLETED)
                .collect(Collectors.groupingBy(Booking::getStationName, Collectors.summingDouble(Booking::getTotalAmount)));
        List<Map<String, Object>> topStations = stationRevenue.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(5)
                .map(entry -> linkedMap("station", entry.getKey(), "revenue", round(entry.getValue()))).toList();
        Map<Integer, Long> hourCounts = bookings.stream().filter(booking -> booking.getStartTime() != null)
                .collect(Collectors.groupingBy(booking -> booking.getStartTime().getHour(), Collectors.counting()));
        int peakHour = hourCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(18);
        return linkedMap(
                "summary", dashboard,
                "dailyRevenue", revenueSince(bookings, LocalDate.now()),
                "weeklyRevenue", revenueSince(bookings, LocalDate.now().minusDays(6)),
                "monthlyRevenue", revenueSince(bookings, LocalDate.now().withDayOfMonth(1)),
                "peakUsageHour", String.format("%02d:00", peakHour),
                "topStations", topStations,
                "customerGrowthPercent", growth(bookings),
                "successfulSessions", bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.COMPLETED).count()
        );
    }

    public CompanyNetworkResponse network(Long accountId) {
        Company company = requireCompany(accountId);
        List<ChargingStation> stations = managedStations(company, accountId);
        List<ChargingConnector> chargers = stations.stream()
                .flatMap(station -> station.getConnectors().stream())
                .toList();
        List<ManagedStationResponse> stationResponses = stations.stream().map(station -> {
            List<ChargingConnector> stationChargers = station.getConnectors();
            return new ManagedStationResponse(station.getId(), station.getName(), station.getCity(), station.getAddress(),
                    station.getHostUserId(), relationship(station, accountId), station.getStatus(), stationChargers.size(),
                    (int) stationChargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.ONLINE).count(),
                    (int) stationChargers.stream().filter(this::needsAttention).count());
        }).toList();
        List<ManagedChargerResponse> chargerResponses = chargers.stream()
                .sorted(Comparator.comparingInt(ChargingConnector::getHealthScore))
                .map(charger -> managedChargerResponse(charger, relationship(charger.getStation(), accountId)))
                .toList();
        int openTickets = (int) maintenanceTicketRepository.findByCompanyIdOrderByUpdatedAtDesc(company.getId()).stream()
                .filter(ticket -> ticket.getStatus() == MaintenanceTicketStatus.OPEN
                        || ticket.getStatus() == MaintenanceTicketStatus.IN_PROGRESS)
                .count();
        return new CompanyNetworkResponse(stations.size(), chargers.size(),
                (int) chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.ONLINE).count(),
                (int) chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.CHARGING).count(),
                (int) chargers.stream().filter(this::needsAttention).count(), openTickets,
                stationResponses, chargerResponses);
    }

    public List<MaintenanceTicketResponse> maintenanceTickets(Long accountId) {
        Company company = requireCompany(accountId);
        return maintenanceTicketRepository.findByCompanyIdOrderByUpdatedAtDesc(company.getId()).stream()
                .map(this::maintenanceTicketResponse)
                .toList();
    }

    @Transactional
    public MaintenanceTicketResponse createMaintenanceTicket(Long accountId, MaintenanceTicketCreateRequest request) {
        Company company = requireVerifiedCompany(accountId);
        ChargingConnector charger = managedCharger(company, accountId, request.chargerId());
        if (maintenanceTicketRepository.existsByCompanyIdAndChargerIdAndStatusIn(company.getId(), charger.getId(),
                List.of(MaintenanceTicketStatus.OPEN, MaintenanceTicketStatus.IN_PROGRESS))) {
            throw new DuplicateResourceException("An active maintenance ticket already exists for " + charger.getChargerCode());
        }
        CompanyMaintenanceTicket ticket = maintenanceTicketRepository.save(CompanyMaintenanceTicket.builder()
                .companyId(company.getId())
                .chargerId(charger.getId())
                .chargerCode(charger.getChargerCode())
                .stationId(charger.getStation().getId())
                .stationName(charger.getStation().getName())
                .city(charger.getStation().getCity())
                .priority(request.priority())
                .status(MaintenanceTicketStatus.OPEN)
                .issue(request.issue().trim())
                .assignedTo(clean(request.assignedTo()))
                .build());
        recordActivity(company, accountId, "CREATE", "MAINTENANCE_TICKET", ticket.getId(),
                "Opened " + ticket.getPriority() + " maintenance ticket for " + charger.getChargerCode());
        return maintenanceTicketResponse(ticket);
    }

    @Transactional
    public MaintenanceTicketResponse updateMaintenanceTicket(Long accountId, Long id,
                                                               MaintenanceTicketUpdateRequest request) {
        Company company = requireVerifiedCompany(accountId);
        CompanyMaintenanceTicket ticket = maintenanceTicketRepository.findByIdAndCompanyId(id, company.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance ticket not found for this company"));
        ticket.setStatus(request.status());
        if (request.priority() != null) ticket.setPriority(request.priority());
        ticket.setAssignedTo(clean(request.assignedTo()));
        ticket.setResolutionNote(clean(request.resolutionNote()));
        ticket.setUpdatedAt(LocalDateTime.now());
        if (request.status() == MaintenanceTicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
            if (request.restoreChargerOnline()) {
                ChargingConnector charger = managedCharger(company, accountId, ticket.getChargerId());
                charger.setStatus(ChargerStatus.ONLINE);
                charger.setMaintenanceMode(false);
                charger.setAvailable(true);
                charger.setFaultCode(null);
                charger.setHealthScore(Math.max(80, charger.getHealthScore()));
                charger.setLastHeartbeat(LocalDateTime.now());
                connectorRepository.save(charger);
            }
        } else {
            ticket.setResolvedAt(null);
        }
        CompanyMaintenanceTicket saved = maintenanceTicketRepository.save(ticket);
        recordActivity(company, accountId, "STATUS_CHANGE", "MAINTENANCE_TICKET", saved.getId(),
                "Moved maintenance ticket for " + saved.getChargerCode() + " to " + saved.getStatus());
        return maintenanceTicketResponse(saved);
    }

    public CompanySettlementResponse settlements(Long accountId) {
        requireCompany(accountId);
        List<Booking> bookings = ownedBookings(accountId);
        Map<Long, Booking> bookingById = bookings.stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity(), (left, right) -> left));
        List<Payment> payments = paymentsForBookings(bookings);
        double collected = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount).sum();
        double pending = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .mapToDouble(Payment::getAmount).sum();
        double refunded = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.REFUNDED)
                .mapToDouble(Payment::getAmount).sum();
        List<CompanySettlementTransactionResponse> transactions = payments.stream()
                .sorted(Comparator.comparing(Payment::getTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(100)
                .map(payment -> {
                    Booking booking = bookingById.get(payment.getBookingId());
                    return new CompanySettlementTransactionResponse(payment.getId(), payment.getBookingId(),
                            booking == null ? "Station payment" : booking.getStationName(), payment.getAmount(),
                            payment.getStatus(), payment.getGatewayTransactionId(), payment.getTimestamp());
                }).toList();
        return new CompanySettlementResponse(round(collected), round(pending), round(refunded),
                round(collected - refunded),
                payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS).count(), transactions);
    }

    public List<CompanyEmployee> getEmployees(Long accountId) {
        return employeeRepository.findByCompanyIdOrderByCreatedAtDesc(requireCompany(accountId).getId());
    }

    public List<CompanyActivityLog> getActivityLogs(Long accountId) {
        return activityLogRepository.findTop100ByCompanyIdOrderByCreatedAtDesc(requireCompany(accountId).getId());
    }

    @Transactional
    public CompanyEmployee createEmployee(Long accountId, EmployeeRequest request) {
        Company company = requireVerifiedCompany(accountId);
        if (employeeRepository.existsByCompanyIdAndEmailIgnoreCase(company.getId(), request.getEmail())) {
            throw new DuplicateResourceException("Employee already exists: " + request.getEmail());
        }
        CompanyEmployee employee = employeeRepository.save(applyEmployee(CompanyEmployee.builder().companyId(company.getId()).build(), request));
        recordActivity(company, accountId, "CREATE", "EMPLOYEE", employee.getId(), "Added employee " + employee.getName() + " as " + employee.getRole());
        return employee;
    }

    @Transactional
    public CompanyEmployee updateEmployee(Long accountId, Long id, EmployeeRequest request) {
        Company company = requireVerifiedCompany(accountId);
        CompanyEmployee employee = employeeRepository.findByIdAndCompanyId(id, company.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for this company"));
        CompanyEmployee saved = employeeRepository.save(applyEmployee(employee, request));
        recordActivity(company, accountId, "UPDATE", "EMPLOYEE", id, "Updated employee " + saved.getName());
        return saved;
    }

    @Transactional
    public void deleteEmployee(Long accountId, Long id) {
        Company company = requireVerifiedCompany(accountId);
        CompanyEmployee employee = employeeRepository.findByIdAndCompanyId(id, company.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for this company"));
        employeeRepository.delete(employee);
        recordActivity(company, accountId, "DELETE", "EMPLOYEE", id, "Removed employee " + employee.getName());
    }

    public Map<String, Object> askAssistant(Long accountId, String rawQuestion) {
        Company company = requireCompany(accountId);
        Map<String, Object> analytics = analytics(accountId);
        Map<?, ?> summary = (Map<?, ?>) analytics.get("summary");
        String question = rawQuestion.toLowerCase(Locale.ROOT);
        List<ChargingStation> suppliedStations = stationRepository.findBySupplierCompanyId(company.getId());
        List<ChargingConnector> suppliedChargers = suppliedStations.stream()
                .flatMap(station -> station.getConnectors().stream()).toList();
        long nationalFaults = suppliedChargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.FAULT
                || charger.getStatus() == ChargerStatus.OFFLINE || charger.getHealthScore() < 70).count();
        List<Map<String, Object>> siteRecommendations = expansionSites();
        String answer;
        if (question.contains("revenue")) {
            answer = "Today's network revenue is ₹" + analytics.get("dailyRevenue") + ". Monthly revenue is ₹" + analytics.get("monthlyRevenue") + ".";
        } else if (question.contains("maintenance") || question.contains("fault") || question.contains("failure")
                || question.contains("offline") || question.contains("nation")) {
            answer = nationalFaults + " chargers need attention across the nationwide company-supplied Host network. "
                    + "Prioritize offline or sub-70 health units before peak demand; your own workspace currently reports " + summary.get("faults") + " fault(s).";
        } else if (question.contains("demand") || question.contains("peak")) {
            answer = "Peak demand is expected around " + analytics.get("peakUsageHour") + ". Keep fast chargers online and reduce maintenance overlap.";
        } else if (question.contains("price")) {
            answer = "Use peak pricing only at high-occupancy stations. Protect student and corporate discounts with station-specific rules.";
        } else if (question.contains("expand") || question.contains("area") || question.contains("location")
                || question.contains("plant") || question.contains("land") || question.contains("commute")
                || question.contains("route") || question.contains("install")) {
            answer = siteRecommendations.isEmpty()
                    ? "No Host land is published yet. Vidyut will rank nationwide listings as soon as Hosts make them discoverable."
                    : "Vidyut ranked " + siteRecommendations.size() + " nationwide Host site(s) by grid readiness, parking capacity and distance from existing active stations. Start with "
                    + siteRecommendations.get(0).get("title") + " in " + siteRecommendations.get(0).get("location") + ".";
        } else if (question.contains("energy")) {
            answer = "The network has delivered " + summary.get("energyDeliveredKwh") + " kWh across recorded sessions.";
        } else {
            answer = "Your network has " + summary.get("totalStations") + " stations, " + summary.get("totalChargers") + " chargers and " + summary.get("utilizationRate") + "% live utilization.";
        }
        return linkedMap("question", rawQuestion, "answer", answer, "generatedAt", LocalDateTime.now(),
                "suppliedStations", suppliedStations.size(), "nationalFaults", nationalFaults,
                "siteRecommendations", siteRecommendations,
                "recommendations", List.of("Review nationwide Host opportunities", "Resolve supplied-network faults", "Validate top sites with a grid and traffic survey"));
    }

    private List<Map<String, Object>> expansionSites() {
        List<ChargingStation> activeStations = stationRepository.findAll().stream()
                .filter(station -> station.getStatus() == StationStatus.ACTIVE).toList();
        return landListingRepository.findByDiscoverableTrueAndStatusIn(List.of(LandListingStatus.APPROVED, LandListingStatus.ACTIVE))
                .stream().map(site -> {
                    double nearestKm = activeStations.stream()
                            .mapToDouble(station -> distanceKm(site.getLatitude(), site.getLongitude(), station.getLatitude(), station.getLongitude()))
                            .min().orElse(100);
                    double score = Math.min(100, site.getAvailableParkingBays() * 6.0
                            + Math.min(45, site.getAvailableLoadKw() * 0.7) + Math.min(35, nearestKm * 0.7));
                    String location = java.util.stream.Stream.of(site.getCity(), site.getState()).filter(Objects::nonNull)
                            .filter(value -> !value.isBlank()).collect(Collectors.joining(", "));
                    return linkedMap("propertyId", site.getId(), "title", site.getTitle(),
                            "location", location.isBlank() ? site.getAddress() : location,
                            "parkingBays", site.getAvailableParkingBays(), "availableLoadKw", site.getAvailableLoadKw(),
                            "nearestActiveStationKm", round(nearestKm), "expansionScore", round(score),
                            "reason", site.getAvailableLoadKw() >= 60
                                    ? "Strong grid headroom with a nationwide Host ready to discuss installation"
                                    : "Underserved Host location; confirm transformer capacity during site survey");
                }).sorted(Comparator.comparingDouble((Map<String, Object> site) -> ((Number) site.get("expansionScore")).doubleValue()).reversed())
                .limit(5).toList();
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        if ((lat1 == 0 && lon1 == 0) || (lat2 == 0 && lon2 == 0)) return 50;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public List<Payout> payouts(Long accountId) {
        requireCompany(accountId);
        return payoutRepository.findByHostUserId(accountId);
    }

    private List<ChargingStation> managedStations(Company company, Long accountId) {
        Map<Long, ChargingStation> managed = new LinkedHashMap<>();
        stationRepository.findByHostUserId(accountId).forEach(station -> managed.put(station.getId(), station));
        stationRepository.findBySupplierCompanyId(company.getId()).forEach(station -> managed.put(station.getId(), station));
        return new ArrayList<>(managed.values());
    }

    private ChargingConnector managedCharger(Company company, Long accountId, Long chargerId) {
        ChargingConnector charger = connectorRepository.findById(chargerId)
                .orElseThrow(() -> new ResourceNotFoundException("Charger not found"));
        ChargingStation station = charger.getStation();
        if (!Objects.equals(station.getHostUserId(), accountId)
                && !Objects.equals(station.getSupplierCompanyId(), company.getId())) {
            throw new ResourceNotFoundException("Charger not found for this company network");
        }
        return charger;
    }

    private boolean needsAttention(ChargingConnector charger) {
        return charger.getStatus() == ChargerStatus.FAULT || charger.getStatus() == ChargerStatus.OFFLINE
                || charger.getHealthScore() < 70 || charger.isMaintenanceMode();
    }

    private String relationship(ChargingStation station, Long accountId) {
        return Objects.equals(station.getHostUserId(), accountId) ? "COMPANY_OPERATED" : "HOST_OPERATED_SUPPLIED";
    }

    private ManagedChargerResponse managedChargerResponse(ChargingConnector charger, String relationship) {
        ChargingStation station = charger.getStation();
        return new ManagedChargerResponse(charger.getId(), station.getId(), station.getName(), station.getCity(),
                charger.getChargerCode(), charger.getType().name(), charger.getPowerKw(), charger.getStatus(),
                charger.isAvailable(), charger.isMaintenanceMode(), charger.getHealthScore(), charger.getFaultCode(),
                charger.getLastHeartbeat(), relationship);
    }

    private MaintenanceTicketResponse maintenanceTicketResponse(CompanyMaintenanceTicket ticket) {
        return new MaintenanceTicketResponse(ticket.getId(), ticket.getChargerId(), ticket.getChargerCode(),
                ticket.getStationId(), ticket.getStationName(), ticket.getCity(), ticket.getPriority(), ticket.getStatus(),
                ticket.getIssue(), ticket.getAssignedTo(), ticket.getResolutionNote(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), ticket.getResolvedAt());
    }

    public byte[] exportReport(Long accountId, String reportType, String format) {
        Map<String, Object> data = "ANALYTICS".equalsIgnoreCase(reportType) ? analytics(accountId) : dashboard(accountId);
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Vidyut Company Report", reportType.toUpperCase(Locale.ROOT)));
        rows.add(List.of("Generated", LocalDateTime.now().toString()));
        flattenRows("", data, rows);
        return "PDF".equalsIgnoreCase(format) ? createPdf(rows) : createXlsx(rows);
    }

    private ChargingConnector applyCharger(ChargingConnector connector, ChargerRequest request) {
        connector.setChargerCode(request.getChargerCode().trim().toUpperCase(Locale.ROOT));
        connector.setType(request.getConnectorType());
        connector.setPowerKw(request.getPowerKw());
        connector.setStatus(request.getStatus() == null ? ChargerStatus.ONLINE : request.getStatus());
        connector.setMaintenanceMode(request.isMaintenanceMode());
        connector.setFirmwareVersion(request.getFirmwareVersion() == null ? "1.0.0" : request.getFirmwareVersion());
        connector.setHealthScore(request.getHealthScore());
        connector.setAvailable(connector.getStatus() == ChargerStatus.ONLINE && !connector.isMaintenanceMode());
        connector.setLastHeartbeat(LocalDateTime.now());
        return connector;
    }

    private CompanyEmployee applyEmployee(CompanyEmployee employee, EmployeeRequest request) {
        employee.setName(request.getName().trim());
        employee.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        employee.setPhone(request.getPhone());
        employee.setRole(request.getRole());
        employee.setActive(request.isActive());
        employee.setPermissions(request.getPermissions());
        return employee;
    }

    private void recordActivity(Company company, Long actorAccountId, String action, String resourceType,
                                Long resourceId, String description) {
        activityLogRepository.save(CompanyActivityLog.builder()
                .companyId(company.getId())
                .actorAccountId(actorAccountId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .description(description)
                .build());
    }

    private Company requireCompany(Long accountId) {
        Company company = companyRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ForbiddenException("Company workspace is not available for this account"));
        if (!company.isActive()) throw new ForbiddenException("Company account is disabled");
        return company;
    }

    private Company requireVerifiedCompany(Long accountId) {
        return verificationService.requireMarketplaceVerified(accountId);
    }

    private ChargingStation ownedStation(Long accountId, Long stationId) {
        return stationRepository.findByIdAndHostUserId(stationId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found for this company"));
    }

    private ChargingConnector ownedCharger(Long accountId, Long id) {
        return connectorRepository.findByIdAndStation_HostUserId(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Charger not found for this company"));
    }

    private List<Booking> ownedBookings(Long accountId) {
        return bookingsForStations(stationRepository.findByHostUserId(accountId));
    }

    private List<Booking> bookingsForStations(List<ChargingStation> stations) {
        List<Long> stationIds = stations.stream().map(ChargingStation::getId).toList();
        return stationIds.isEmpty() ? List.of() : bookingRepository.findByStationIdInOrderByStartTimeDesc(stationIds);
    }

    private List<Payment> paymentsForBookings(List<Booking> bookings) {
        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        return bookingIds.isEmpty() ? List.of() : paymentRepository.findByBookingIdIn(bookingIds);
    }

    private ChargerResponse mapCharger(ChargingConnector connector) {
        return ChargerResponse.builder().id(connector.getId()).stationId(connector.getStation().getId())
                .stationName(connector.getStation().getName()).chargerCode(connector.getChargerCode())
                .connectorType(connector.getType()).powerKw(connector.getPowerKw()).available(connector.isAvailable())
                .status(connector.getStatus()).maintenanceMode(connector.isMaintenanceMode())
                .firmwareVersion(connector.getFirmwareVersion()).healthScore(connector.getHealthScore())
                .lastHeartbeat(connector.getLastHeartbeat()).build();
    }

    private BookingResponse mapBooking(Booking booking) {
        return BookingResponse.builder().id(booking.getId()).userId(booking.getUserId()).stationId(booking.getStationId())
                .vehicleId(booking.getVehicleId()).stationName(booking.getStationName()).stationAddress(booking.getStationAddress())
                .startTime(booking.getStartTime()).durationHours(booking.getDurationHours()).totalAmount(booking.getTotalAmount())
                .kwhDelivered(booking.getKwhDelivered()).status(booking.getStatus()).createdAt(booking.getCreatedAt()).build();
    }

    private List<Map<String, Object>> alerts(List<ChargingConnector> chargers) {
        return chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.FAULT
                        || charger.getStatus() == ChargerStatus.OFFLINE || charger.getHealthScore() < 70)
                .sorted(Comparator.comparingInt(ChargingConnector::getHealthScore))
                .limit(10).map(charger -> linkedMap("chargerId", charger.getId(), "chargerCode", charger.getChargerCode(),
                        "station", charger.getStation().getName(), "status", charger.getStatus(), "healthScore", charger.getHealthScore(),
                        "message", charger.getStatus() == ChargerStatus.FAULT ? "Fault detected" : "Charger needs attention"))
                .toList();
    }

    private double revenueSince(List<Booking> bookings, LocalDate start) {
        return round(bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.COMPLETED
                        && booking.getStartTime() != null && !booking.getStartTime().toLocalDate().isBefore(start))
                .mapToDouble(Booking::getTotalAmount).sum());
    }

    private double growth(List<Booking> bookings) {
        long recent = bookings.stream().filter(booking -> booking.getCreatedAt() != null
                && booking.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30))).map(Booking::getUserId).distinct().count();
        long previous = bookings.stream().filter(booking -> booking.getCreatedAt() != null
                && booking.getCreatedAt().isAfter(LocalDateTime.now().minusDays(60))
                && booking.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30))).map(Booking::getUserId).distinct().count();
        return previous == 0 ? (recent == 0 ? 0 : 100) : round((recent - previous) * 100.0 / previous);
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }

    private String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, T> linkedMap(Object... pairs) {
        Map<String, T> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) map.put(String.valueOf(pairs[index]), (T) pairs[index + 1]);
        return map;
    }

    private void flattenRows(String prefix, Object value, List<List<String>> rows) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> flattenRows(prefix.isBlank() ? String.valueOf(key) : prefix + "." + key, nested, rows));
        } else if (value instanceof Collection<?> collection) {
            rows.add(List.of(prefix, collection.toString()));
        } else {
            rows.add(List.of(prefix, String.valueOf(value)));
        }
    }

    private byte[] createXlsx(List<List<String>> rows) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                zipEntry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
                zipEntry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
                zipEntry(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Company report\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
                zipEntry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
                StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
                for (int r = 0; r < rows.size(); r++) {
                    sheet.append("<row r=\"").append(r + 1).append("\">");
                    for (int c = 0; c < rows.get(r).size(); c++) {
                        sheet.append("<c r=\"").append((char) ('A' + c)).append(r + 1).append("\" t=\"inlineStr\"><is><t>")
                                .append(xml(rows.get(r).get(c))).append("</t></is></c>");
                    }
                    sheet.append("</row>");
                }
                sheet.append("</sheetData></worksheet>");
                zipEntry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create Excel report", exception);
        }
    }

    private byte[] createPdf(List<List<String>> rows) {
        StringBuilder content = new StringBuilder("BT /F1 10 Tf 50 790 Td 14 TL ");
        for (List<String> row : rows.stream().limit(48).toList()) {
            content.append("(").append(pdf(String.join(": ", row))).append(") Tj T* ");
        }
        content.append("ET");
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + content.toString().getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + content + "\nendstream"
        );
        StringBuilder document = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(document.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            document.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = document.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        document.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) document.append(String.format("%010d 00000 n \n", offsets.get(i)));
        document.append("trailer << /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
        return document.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private void zipEntry(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String xml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private String pdf(String value) { return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replaceAll("[^\\x20-\\x7E]", "?"); }
}
