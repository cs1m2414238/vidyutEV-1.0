package com.vidyut.host.service;

import com.vidyut.account.entity.*;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.booking.entity.*;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.common.exception.*;
import com.vidyut.host.dto.*;
import com.vidyut.host.entity.HostReview;
import com.vidyut.host.repository.HostReviewRepository;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.payment.entity.*;
import com.vidyut.payment.repository.PaymentRepository;
import com.vidyut.payment.repository.PayoutRepository;
import com.vidyut.station.dto.*;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.*;
import com.vidyut.station.service.ChargingStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.zip.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HostOperationsService {
    private final HostProfileRepository hostProfileRepository;
    private final AccountRepository accountRepository;
    private final EvUserProfileRepository evUserProfileRepository;
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final ChargingStationService stationService;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PayoutRepository payoutRepository;
    private final HostReviewRepository reviewRepository;
    private final NotificationService notificationService;

    public HostProfileResponse profile(Long accountId) { return mapProfile(requireHost(accountId)); }

    @Transactional
    public HostProfileResponse updateProfile(Long accountId, HostProfileUpdateRequest request) {
        HostProfile profile = requireHost(accountId);
        profile.setDisplayName(request.getDisplayName().trim());
        profile.setPhone(request.getPhone());
        profile.setAddress(request.getAddress());
        profile.setBio(request.getBio());
        return mapProfile(hostProfileRepository.save(profile));
    }

    @Transactional
    public HostProfileResponse submitVerification(Long accountId, HostVerificationRequest request) {
        HostProfile profile = requireHost(accountId);
        profile.setIdentityType(request.getIdentityType().trim().toUpperCase(Locale.ROOT));
        profile.setIdentityLast4(request.getIdentityLast4().trim().toUpperCase(Locale.ROOT));
        profile.setKycDocumentUrl(request.getKycDocumentUrl().trim());
        profile.setVerified(false);
        profile.setVerificationStatus(HostVerificationStatus.PENDING);
        profile.setVerificationRequestedAt(LocalDateTime.now());
        notificationService.sendNotification(accountId, "Host KYC submitted",
                "Your identity documents are queued for verification.", NotificationType.SYSTEM_ALERT);
        return mapProfile(hostProfileRepository.save(profile));
    }

    @Transactional
    public HostProfileResponse approveVerification(Long accountId) {
        HostProfile profile = requireHost(accountId);
        profile.setVerified(true);
        profile.setVerificationStatus(HostVerificationStatus.VERIFIED);
        notificationService.sendNotification(accountId, "Host KYC verified",
                "You can now register and operate private chargers.", NotificationType.SYSTEM_ALERT);
        return mapProfile(hostProfileRepository.save(profile));
    }

    @Transactional
    public HostProfileResponse updateBank(Long accountId, HostBankRequest request) {
        HostProfile profile = requireHost(accountId);
        String digits = request.getAccountNumber().replaceAll("\\D", "");
        profile.setBankAccountHolder(request.getAccountHolder().trim());
        profile.setBankName(request.getBankName().trim());
        profile.setBankAccountLast4(digits.substring(Math.max(0, digits.length() - 4)));
        profile.setIfscCode(request.getIfscCode().trim().toUpperCase(Locale.ROOT));
        profile.setPayoutUpi(request.getPayoutUpi());
        profile.setBankVerified(true);
        return mapProfile(hostProfileRepository.save(profile));
    }

    @Transactional
    public HostProfileResponse updateSettings(Long accountId, HostSettingsRequest request) {
        HostProfile profile = requireHost(accountId);
        profile.setEmailNotifications(request.isEmailNotifications());
        profile.setPushNotifications(request.isPushNotifications());
        profile.setAutoAvailability(request.isAutoAvailability());
        return mapProfile(hostProfileRepository.save(profile));
    }

    @Transactional
    public String requestEmailCode(Long accountId) {
        HostProfile profile = requireHost(accountId);
        if (profile.getAccount().isEmailVerified()) return "Email is already verified";
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        profile.setEmailVerificationCodeHash(hash(code));
        profile.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(15));
        hostProfileRepository.save(profile);
        notificationService.sendNotification(accountId, "Host email verification",
                "Your verification code is " + code + ". It expires in 15 minutes.", NotificationType.SYSTEM_ALERT);
        return "Verification code sent";
    }

    @Transactional
    public HostProfileResponse confirmEmailCode(Long accountId, String code) {
        HostProfile profile = requireHost(accountId);
        if (profile.getAccount().isEmailVerified()) return mapProfile(profile);
        if (profile.getEmailVerificationExpiresAt() == null
                || profile.getEmailVerificationExpiresAt().isBefore(LocalDateTime.now())
                || !hash(code).equals(profile.getEmailVerificationCodeHash())) {
            throw new BadRequestException("Verification code is invalid or expired");
        }
        profile.getAccount().setEmailVerified(true);
        accountRepository.save(profile.getAccount());
        profile.setEmailVerificationCodeHash(null);
        profile.setEmailVerificationExpiresAt(null);
        return mapProfile(hostProfileRepository.save(profile));
    }

    public List<StationResponse> stations(Long accountId) {
        requireHost(accountId);
        return stationService.getStationsByOwner(accountId);
    }

    @Transactional
    public StationResponse createStation(Long accountId, StationCreateRequest request) {
        requireOperationalHost(accountId);
        if (stationRepository.findByHostUserId(accountId).size() >= 10) {
            throw new BadRequestException("Host accounts can manage up to 10 private charging locations");
        }
        return stationService.createStation(request, accountId);
    }

    @Transactional
    public StationResponse updateStation(Long accountId, Long id, StationUpdateRequest request) {
        requireOperationalHost(accountId);
        return stationService.updateStation(id, accountId, request);
    }

    @Transactional
    public void deleteStation(Long accountId, Long id) {
        requireOperationalHost(accountId);
        stationService.deleteStation(id, accountId);
    }

    @Transactional
    public StationResponse updateAvailability(Long accountId, Long stationId, HostAvailabilityRequest request) {
        requireOperationalHost(accountId);
        ChargingStation station = ownedStation(accountId, stationId);
        station.setAvailability(request.isEmergencyDisabled() ? StationAvailability.UNAVAILABLE : request.getAvailability());
        station.setEmergencyDisabled(request.isEmergencyDisabled());
        station.setAutoAvailability(request.isAutoAvailability());
        station.setWeeklySchedule(request.getWeeklySchedule());
        station.setHolidaySchedule(request.getHolidaySchedule());
        station.setChargingInstructions(request.getChargingInstructions());
        station.setBookingSlotMinutes(request.getBookingSlotMinutes());
        station.setStatus(request.isEmergencyDisabled() ? StationStatus.OFFLINE : StationStatus.ACTIVE);
        stationRepository.save(station);
        return stationService.getStationById(stationId);
    }

    @Transactional
    public Map<String, Object> updateChargerStatus(Long accountId, Long connectorId, HostChargerStatusRequest request) {
        requireOperationalHost(accountId);
        ChargingConnector connector = ownedConnector(accountId, connectorId);
        connector.setStatus(request.getStatus());
        connector.setAvailable(request.getStatus() == ChargerStatus.ONLINE);
        connector.setCurrentPowerKw(request.getCurrentPowerKw());
        connector.setSessionEnergyKwh(request.getSessionEnergyKwh());
        connector.setHealthScore(request.getHealthScore());
        connector.setFaultCode(request.getFaultCode());
        connector.setLastHeartbeat(LocalDateTime.now());
        if (request.getStatus() == ChargerStatus.CHARGING && connector.getSessionStartedAt() == null) connector.setSessionStartedAt(LocalDateTime.now());
        if (request.getStatus() != ChargerStatus.CHARGING) connector.setSessionStartedAt(null);
        connectorRepository.save(connector);
        if (request.getStatus() == ChargerStatus.FAULT) {
            notificationService.sendNotification(accountId, "Charger fault detected",
                    connector.getChargerCode() + " reported " + Objects.toString(request.getFaultCode(), "a fault"), NotificationType.FAULT_ALERT);
        }
        return mapConnector(connector);
    }

    public List<HostBookingResponse> bookings(Long accountId) {
        requireHost(accountId);
        return ownedBookings(accountId).stream().map(this::mapBooking).toList();
    }

    @Transactional
    public HostBookingResponse updateBooking(Long accountId, Long bookingId, BookingStatus status) {
        requireOperationalHost(accountId);
        Booking booking = ownedBooking(accountId, bookingId);
        booking.setStatus(status);
        bookingRepository.save(booking);
        NotificationType type = switch (status) {
            case CANCELLED -> NotificationType.BOOKING_CANCELLED;
            case IN_PROGRESS -> NotificationType.CHARGING_STARTED;
            case COMPLETED -> NotificationType.CHARGING_COMPLETED;
            default -> NotificationType.BOOKING_CONFIRMED;
        };
        notificationService.sendNotification(booking.getUserId(), "Booking " + status.name().toLowerCase(Locale.ROOT),
                booking.getStationName() + " · " + booking.getStartTime(), type);
        if (status == BookingStatus.COMPLETED) {
            notificationService.sendNotification(accountId, "Payment received",
                    "₹" + round(booking.getTotalAmount()) + " earned from booking #" + booking.getId(), NotificationType.PAYMENT_RECEIVED);
        }
        return mapBooking(booking);
    }

    @Transactional
    public HostBookingResponse reschedule(Long accountId, Long bookingId, LocalDateTime startTime) {
        requireOperationalHost(accountId);
        Booking booking = ownedBooking(accountId, bookingId);
        booking.setStartTime(startTime);
        bookingRepository.save(booking);
        notificationService.sendNotification(booking.getUserId(), "Booking rescheduled",
                "Your charging slot now starts at " + startTime, NotificationType.BOOKING_CONFIRMED);
        return mapBooking(booking);
    }

    public Map<String, Object> dashboard(Long accountId) {
        HostProfile profile = requireHost(accountId);
        List<ChargingStation> stations = stationRepository.findByHostUserId(accountId);
        List<ChargingConnector> connectors = connectorRepository.findByStation_HostUserId(accountId);
        List<Booking> bookings = bookingsFor(stations);
        Map<String, Object> earnings = earnings(accountId);
        long upcoming = bookings.stream().filter(b -> b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED).count();
        long active = bookings.stream().filter(b -> b.getStatus() == BookingStatus.IN_PROGRESS).count();
        double energy = bookings.stream().mapToDouble(Booking::getKwhDelivered).sum();
        double uptime = connectors.isEmpty() ? 0 : round(connectors.stream().filter(c -> c.getStatus() == ChargerStatus.ONLINE || c.getStatus() == ChargerStatus.CHARGING).count() * 100.0 / connectors.size());
        return linkedMap("displayName", profile.getDisplayName(), "verified", profile.isVerified(),
                "totalChargers", connectors.size(), "onlineChargers", connectors.stream().filter(c -> c.getStatus() == ChargerStatus.ONLINE).count(),
                "activeSessions", active, "upcomingBookings", upcoming, "energyDeliveredKwh", round(energy), "uptimePercent", uptime,
                "todayEarnings", earnings.get("daily"), "monthlyEarnings", earnings.get("monthly"), "pendingPayout", earnings.get("pendingPayout"),
                "reputationScore", profile.getReputationScore(), "alerts", monitoring(accountId).stream().filter(item -> "FAULT".equals(item.get("status")) || ((Number)item.get("healthScore")).intValue() < 70).toList());
    }

    public Map<String, Object> earnings(Long accountId) {
        requireHost(accountId);
        List<Booking> bookings = ownedBookings(accountId);
        List<Booking> completed = bookings.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).toList();
        double daily = revenueSince(completed, LocalDate.now());
        double weekly = revenueSince(completed, LocalDate.now().minusDays(6));
        double monthly = revenueSince(completed, LocalDate.now().withDayOfMonth(1));
        double total = completed.stream().mapToDouble(Booking::getTotalAmount).sum();
        List<Payout> payouts = payoutRepository.findByHostUserId(accountId);
        double withdrawn = payouts.stream().mapToDouble(Payout::getAmount).sum();
        double available = Math.max(0, total - withdrawn);
        double pending = payouts.stream().filter(p -> "PENDING".equalsIgnoreCase(p.getStatus())).mapToDouble(Payout::getAmount).sum();
        int year = LocalDate.now().getMonthValue() >= 4 ? LocalDate.now().getYear() : LocalDate.now().getYear() - 1;
        return linkedMap("daily", round(daily), "weekly", round(weekly), "monthly", round(monthly), "lifetime", round(total),
                "availableBalance", round(available), "pendingPayout", round(pending), "taxWithheld", round(total * .01),
                "financialYear", year + "-" + String.valueOf(year + 1).substring(2), "payouts", payouts,
                "transactions", completed.stream().map(b -> linkedMap("bookingId", b.getId(), "station", b.getStationName(), "amount", b.getTotalAmount(), "timestamp", b.getStartTime(), "status", "EARNED")).toList());
    }

    @Transactional
    public Payout withdraw(Long accountId, double amount) {
        HostProfile profile = requireOperationalHost(accountId);
        if (!profile.isBankVerified()) throw new ForbiddenException("Verify a bank account before withdrawing earnings");
        double available = ((Number) earnings(accountId).get("availableBalance")).doubleValue();
        if (amount > available) throw new BadRequestException("Withdrawal exceeds available balance");
        Payout payout = payoutRepository.save(Payout.builder().hostUserId(accountId).amount(round(amount)).status("PENDING").build());
        notificationService.sendNotification(accountId, "Withdrawal requested", "₹" + round(amount) + " will be processed to bank account ending " + profile.getBankAccountLast4(), NotificationType.SYSTEM_ALERT);
        return payout;
    }

    public List<Map<String, Object>> monitoring(Long accountId) {
        requireHost(accountId);
        return connectorRepository.findByStation_HostUserId(accountId).stream().map(this::mapConnector).toList();
    }

    public List<HostReview> reviews(Long accountId) {
        requireHost(accountId);
        return reviewRepository.findByHostAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional
    public HostReview replyReview(Long accountId, Long reviewId, String reply) {
        requireHost(accountId);
        HostReview review = ownedReview(accountId, reviewId);
        review.setHostReply(reply.trim());
        return reviewRepository.save(review);
    }

    @Transactional
    public HostReview reportReview(Long accountId, Long reviewId, String reason) {
        requireHost(accountId);
        HostReview review = ownedReview(accountId, reviewId);
        review.setReported(true);
        review.setReportReason(reason.trim());
        return reviewRepository.save(review);
    }

    public Map<String, Object> assistant(Long accountId, String rawQuestion) {
        Map<String, Object> dashboard = dashboard(accountId);
        Map<String, Object> earnings = earnings(accountId);
        List<Booking> bookings = ownedBookings(accountId);
        Map<Integer, Long> hours = new HashMap<>();
        bookings.stream().filter(b -> b.getStartTime() != null).forEach(b -> hours.merge(b.getStartTime().getHour(), 1L, Long::sum));
        int peak = hours.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(18);
        String q = rawQuestion.toLowerCase(Locale.ROOT);
        String answer;
        if (q.contains("earning") || q.contains("revenue") || q.contains("increase")) {
            answer = "Open availability around " + String.format("%02d:00", peak) + "–" + String.format("%02d:00", Math.min(23, peak + 4)) + ", review weekday pricing by ₹1/kWh, and protect high-uptime slots. Current monthly earnings are ₹" + earnings.get("monthly") + ".";
        } else if (q.contains("price")) {
            answer = "Compare utilization before changing price. A ₹1/kWh weekday adjustment is a safe first experiment for a private charger.";
        } else if (q.contains("fault") || q.contains("health")) {
            answer = dashboard.get("alerts") + " charger alerts need attention. Emergency-disable any unsafe connector before accepting bookings.";
        } else if (q.contains("peak") || q.contains("demand") || q.contains("availability")) {
            answer = "Recorded demand peaks near " + String.format("%02d:00", peak) + ". Keep at least four hours open around that window.";
        } else if (q.contains("forecast")) {
            double forecast = ((Number) earnings.get("monthly")).doubleValue() * 1.18;
            answer = "With improved peak-hour availability, the current scenario forecast is ₹" + round(forecast) + " for the next comparable period (+18% scenario).";
        } else {
            answer = "You have " + dashboard.get("totalChargers") + " chargers, " + dashboard.get("upcomingBookings") + " upcoming bookings and " + dashboard.get("uptimePercent") + "% uptime.";
        }
        return linkedMap("question", rawQuestion, "answer", answer, "peakHour", String.format("%02d:00", peak),
                "recommendations", List.of("Keep 6 PM–10 PM available", "Review weekday price by ₹1/kWh", "Resolve low-health alerts before peak demand"), "generatedAt", LocalDateTime.now());
    }

    public byte[] exportReport(Long accountId, String reportType, String format) {
        Map<String, Object> data = "EARNINGS".equalsIgnoreCase(reportType) ? earnings(accountId) : dashboard(accountId);
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Vidyut Host Report", reportType.toUpperCase(Locale.ROOT)));
        rows.add(List.of("Generated", LocalDateTime.now().toString()));
        data.forEach((key, value) -> rows.add(List.of(key, String.valueOf(value))));
        return "PDF".equalsIgnoreCase(format) ? pdf(rows) : xlsx(rows);
    }

    private HostProfile requireHost(Long accountId) {
        HostProfile profile = hostProfileRepository.findById(accountId)
                .orElseThrow(() -> new ForbiddenException("Host workspace is not available for this account"));
        if (!profile.getAccount().isEnabled()) throw new ForbiddenException("Host account is disabled");
        return profile;
    }

    private HostProfile requireOperationalHost(Long accountId) {
        HostProfile profile = requireHost(accountId);
        if (!profile.getAccount().isEmailVerified()) throw new ForbiddenException("Verify your email before managing chargers");
        if (!profile.isVerified() || profile.getVerificationStatus() != HostVerificationStatus.VERIFIED) throw new ForbiddenException("Host KYC must be approved before managing chargers");
        return profile;
    }

    private ChargingStation ownedStation(Long accountId, Long stationId) {
        return stationRepository.findByIdAndHostUserId(stationId, accountId).orElseThrow(() -> new ResourceNotFoundException("Charger location not found for this host"));
    }

    private ChargingConnector ownedConnector(Long accountId, Long connectorId) {
        return connectorRepository.findByIdAndStation_HostUserId(connectorId, accountId).orElseThrow(() -> new ResourceNotFoundException("Connector not found for this host"));
    }

    private Booking ownedBooking(Long accountId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        ownedStation(accountId, booking.getStationId());
        return booking;
    }

    private HostReview ownedReview(Long accountId, Long reviewId) {
        return reviewRepository.findByIdAndHostAccountId(reviewId, accountId).orElseThrow(() -> new ResourceNotFoundException("Review not found for this host"));
    }

    private List<Booking> ownedBookings(Long accountId) { return bookingsFor(stationRepository.findByHostUserId(accountId)); }
    private List<Booking> bookingsFor(List<ChargingStation> stations) {
        List<Long> ids = stations.stream().map(ChargingStation::getId).toList();
        return ids.isEmpty() ? List.of() : bookingRepository.findByStationIdInOrderByStartTimeDesc(ids);
    }

    private HostBookingResponse mapBooking(Booking booking) {
        Account customer = accountRepository.findById(booking.getUserId()).orElse(null);
        String name = evUserProfileRepository.findById(booking.getUserId()).map(EvUserProfile::getFullName).orElse("EV customer");
        return HostBookingResponse.builder().id(booking.getId()).stationId(booking.getStationId()).stationName(booking.getStationName())
                .customerAccountId(booking.getUserId()).customerName(name).customerEmail(customer == null ? null : customer.getEmail())
                .startTime(booking.getStartTime()).durationHours(booking.getDurationHours()).totalAmount(booking.getTotalAmount())
                .kwhDelivered(booking.getKwhDelivered()).status(booking.getStatus()).build();
    }

    private Map<String, Object> mapConnector(ChargingConnector connector) {
        long duration = connector.getSessionStartedAt() == null ? 0 : Duration.between(connector.getSessionStartedAt(), LocalDateTime.now()).toMinutes();
        return linkedMap("id", connector.getId(), "stationId", connector.getStation().getId(), "stationName", connector.getStation().getName(),
                "chargerCode", connector.getChargerCode(), "connectorType", connector.getType(), "powerKw", connector.getPowerKw(),
                "status", connector.getStatus(), "available", connector.isAvailable(), "currentPowerKw", connector.getCurrentPowerKw(),
                "sessionEnergyKwh", connector.getSessionEnergyKwh(), "sessionDurationMinutes", duration, "healthScore", connector.getHealthScore(),
                "faultCode", connector.getFaultCode(), "lastHeartbeat", connector.getLastHeartbeat());
    }

    private HostProfileResponse mapProfile(HostProfile profile) {
        return HostProfileResponse.builder().accountId(profile.getAccountId()).email(profile.getAccount().getEmail())
                .emailVerified(profile.getAccount().isEmailVerified()).displayName(profile.getDisplayName()).phone(profile.getPhone())
                .address(profile.getAddress()).bio(profile.getBio()).verificationStatus(profile.getVerificationStatus())
                .kycDocumentUrl(profile.getKycDocumentUrl()).identityType(profile.getIdentityType()).identityLast4(profile.getIdentityLast4())
                .verificationRequestedAt(profile.getVerificationRequestedAt()).bankAccountHolder(profile.getBankAccountHolder())
                .bankName(profile.getBankName()).bankAccountLast4(profile.getBankAccountLast4()).ifscCode(profile.getIfscCode())
                .payoutUpi(profile.getPayoutUpi()).bankVerified(profile.isBankVerified()).emailNotifications(profile.isEmailNotifications())
                .pushNotifications(profile.isPushNotifications()).autoAvailability(profile.isAutoAvailability()).reputationScore(profile.getReputationScore()).build();
    }

    private double revenueSince(List<Booking> bookings, LocalDate date) {
        return round(bookings.stream().filter(b -> b.getStartTime() != null && !b.getStartTime().toLocalDate().isBefore(date)).mapToDouble(Booking::getTotalAmount).sum());
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("Unable to secure verification code", exception); }
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private LinkedHashMap<String, Object> linkedMap(Object... values) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) map.put(String.valueOf(values[index]), values[index + 1]);
        return map;
    }

    private byte[] pdf(List<List<String>> rows) {
        try {
            StringBuilder text = new StringBuilder("BT /F1 11 Tf 40 800 Td ");
            for (List<String> row : rows) text.append('(').append(escapePdf(String.join(": ", row))).append(") Tj 0 -17 Td ");
            text.append("ET");
            byte[] stream = text.toString().getBytes(StandardCharsets.ISO_8859_1);
            List<String> objects = List.of("<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                    "<< /Length " + stream.length + " >>\nstream\n" + new String(stream, StandardCharsets.ISO_8859_1) + "\nendstream",
                    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
            List<Integer> offsets = new ArrayList<>();
            for (int i = 0; i < objects.size(); i++) { offsets.add(out.size()); out.write(((i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1)); }
            int xref = out.size(); out.write(("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n").getBytes(StandardCharsets.ISO_8859_1));
            for (int offset : offsets) out.write(String.format("%010d 00000 n \n", offset).getBytes(StandardCharsets.ISO_8859_1));
            out.write(("trailer << /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF").getBytes(StandardCharsets.ISO_8859_1)); return out.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("Unable to create PDF", exception); }
    }

    private byte[] xlsx(List<List<String>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            zipEntry(zip, "[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            zipEntry(zip, "_rels/.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            zipEntry(zip, "xl/workbook.xml", "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Host Report\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            zipEntry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            for (int r = 0; r < rows.size(); r++) { sheet.append("<row r=\"").append(r + 1).append("\">"); for (int c = 0; c < rows.get(r).size(); c++) sheet.append("<c r=\"").append((char)('A' + c)).append(r + 1).append("\" t=\"inlineStr\"><is><t>").append(xml(rows.get(r).get(c))).append("</t></is></c>"); sheet.append("</row>"); }
            sheet.append("</sheetData></worksheet>"); zipEntry(zip, "xl/worksheets/sheet1.xml", sheet.toString()); zip.finish(); return out.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("Unable to create Excel report", exception); }
    }

    private void zipEntry(ZipOutputStream zip, String name, String value) throws Exception { zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
    private String escapePdf(String value) { return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("₹", "INR "); }
    private String xml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
