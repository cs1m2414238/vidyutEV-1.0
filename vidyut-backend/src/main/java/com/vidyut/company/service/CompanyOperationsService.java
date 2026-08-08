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
import com.vidyut.company.entity.VerificationStatus;
import com.vidyut.company.repository.CompanyEmployeeRepository;
import com.vidyut.company.repository.CompanyActivityLogRepository;
import com.vidyut.company.repository.CompanyRepository;
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
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final ChargingStationService stationService;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PayoutRepository payoutRepository;

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
        Map<String, Object> analytics = analytics(accountId);
        Map<?, ?> summary = (Map<?, ?>) analytics.get("summary");
        String question = rawQuestion.toLowerCase(Locale.ROOT);
        String answer;
        if (question.contains("revenue")) {
            answer = "Today's network revenue is ₹" + analytics.get("dailyRevenue") + ". Monthly revenue is ₹" + analytics.get("monthlyRevenue") + ".";
        } else if (question.contains("maintenance") || question.contains("fault")) {
            answer = summary.get("faults") + " chargers need attention. Prioritize the lowest health scores before peak demand.";
        } else if (question.contains("demand") || question.contains("peak")) {
            answer = "Peak demand is expected around " + analytics.get("peakUsageHour") + ". Keep fast chargers online and reduce maintenance overlap.";
        } else if (question.contains("price")) {
            answer = "Use peak pricing only at high-occupancy stations. Protect student and corporate discounts with station-specific rules.";
        } else if (question.contains("expand") || question.contains("area")) {
            answer = "Expansion should favor cities with high queue counts and utilization. Review the top-performing station list before acquiring a site.";
        } else if (question.contains("energy")) {
            answer = "The network has delivered " + summary.get("energyDeliveredKwh") + " kWh across recorded sessions.";
        } else {
            answer = "Your network has " + summary.get("totalStations") + " stations, " + summary.get("totalChargers") + " chargers and " + summary.get("utilizationRate") + "% live utilization.";
        }
        return linkedMap("question", rawQuestion, "answer", answer, "generatedAt", LocalDateTime.now(),
                "recommendations", List.of("Resolve critical faults", "Protect the peak-hour charger pool", "Review dynamic pricing weekly"));
    }

    public List<Payout> payouts(Long accountId) {
        requireCompany(accountId);
        return payoutRepository.findByHostUserId(accountId);
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
        Company company = requireCompany(accountId);
        if (!company.getAccount().isEmailVerified()) {
            throw new ForbiddenException("Company email must be verified before managing operations");
        }
        if (company.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ForbiddenException("Business verification must be approved before managing company operations");
        }
        return company;
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
