package com.vidyut.admin.service;

import com.vidyut.account.entity.*;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.admin.dto.*;
import com.vidyut.admin.entity.*;
import com.vidyut.admin.repository.*;
import com.vidyut.autopilot.repository.AutopilotTripRepository;
import com.vidyut.autopilot.repository.RouteExperienceRepository;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.DuplicateResourceException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.company.dto.CompanyVerificationResponse;
import com.vidyut.company.dto.CompanyVerificationReviewRequest;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.company.service.CompanyVerificationService;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.marketplace.entity.ProductApprovalStatus;
import com.vidyut.marketplace.repository.ChargerProductRepository;
import com.vidyut.marketplace.repository.InstallationRequestRepository;
import com.vidyut.payment.entity.PaymentStatus;
import com.vidyut.payment.repository.PaymentRepository;
import com.vidyut.session.repository.ChargingSessionRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPortalService {
    private final AdminAccessService access;
    private final AccountRepository accountRepository;
    private final HostProfileRepository hostRepository;
    private final CompanyRepository companyRepository;
    private final CompanyVerificationService companyVerificationService;
    private final LandListingRepository landRepository;
    private final ChargerProductRepository productRepository;
    private final ChargingStationRepository stationRepository;
    private final InstallationRequestRepository installationRepository;
    private final BookingRepository bookingRepository;
    private final ChargingSessionRepository sessionRepository;
    private final PaymentRepository paymentRepository;
    private final AutopilotTripRepository tripRepository;
    private final RouteExperienceRepository experienceRepository;
    private final AdminAccountRepository adminRepository;
    private final AdminAuditLogRepository auditRepository;
    private final AdminAnnouncementRepository announcementRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminPortalSnapshot snapshot() {
        AdminAccount admin = access.require(AdminCapability.OVERVIEW);
        Set<AdminCapability> caps = access.capabilities(admin.getAdminRole());
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("accounts", accountRepository.count());
        metrics.put("companies", companyRepository.count());
        metrics.put("hosts", hostRepository.count());
        metrics.put("properties", landRepository.count());
        metrics.put("products", productRepository.count());
        metrics.put("stations", stationRepository.count());
        metrics.put("installations", installationRepository.count());
        metrics.put("bookings", bookingRepository.count());
        metrics.put("sessions", sessionRepository.count());
        metrics.put("payments", paymentRepository.count());
        metrics.put("autopilotTrips", tripRepository.count());
        metrics.put("routeExperiences", experienceRepository.count());

        List<Map<String, Object>> accounts = caps.contains(AdminCapability.ACCOUNTS) ? accountRepository.findAll().stream()
                .map(item -> map("id", item.getId(), "email", item.getEmail(), "accountType", item.getAccountType(),
                        "roles", item.getRoles(), "enabled", item.isEnabled(), "emailVerified", item.isEmailVerified(),
                        "createdAt", item.getCreatedAt())).toList() : List.of();
        List<CompanyVerificationResponse> companies = caps.contains(AdminCapability.VERIFICATIONS)
                ? companyVerificationService.reviewQueue() : List.of();
        List<Map<String, Object>> hosts = caps.contains(AdminCapability.VERIFICATIONS) ? hostRepository.findAll().stream()
                .filter(item -> item.getVerificationStatus() != HostVerificationStatus.VERIFIED)
                .map(item -> map("accountId", item.getAccountId(), "name", item.getDisplayName(),
                        "email", item.getAccount().getEmail(), "phone", item.getPhone(), "status", item.getVerificationStatus(),
                        "identityType", item.getIdentityType(), "identityLast4", item.getIdentityLast4(),
                        "kycDocumentUrl", item.getKycDocumentUrl(), "requestedAt", item.getVerificationRequestedAt())).toList() : List.of();
        List<Map<String, Object>> properties = caps.contains(AdminCapability.VERIFICATIONS) ? landRepository.findAll().stream()
                .map(item -> map("id", item.getId(), "hostAccountId", item.getHostUserId(), "title", item.getTitle(),
                        "address", item.getAddress(), "city", item.getCity(), "state", item.getState(), "status", item.getStatus(),
                        "ownershipType", item.getOwnershipType(), "ownershipDocumentUrl", item.getOwnershipDocumentUrl(),
                        "discoverable", item.isDiscoverable(), "reviewNote", item.getAdminReviewNote())).toList() : List.of();
        List<Map<String, Object>> products = caps.contains(AdminCapability.VERIFICATIONS) || caps.contains(AdminCapability.OPERATIONS)
                ? productRepository.findAll().stream().map(item -> map("id", item.getId(), "companyId", item.getCompany().getId(),
                        "company", item.getCompany().getCompanyName(), "model", item.getModelName(), "manufacturer", item.getManufacturer(),
                        "connector", item.getConnectorType(), "powerKw", item.getPowerKw(), "approvalStatus", item.getApprovalStatus(),
                        "complianceDocumentUrl", item.getComplianceDocumentUrl(), "reviewNote", item.getAdminReviewNote(),
                        "active", item.isActive())).toList() : List.of();
        List<Map<String, Object>> stations = caps.contains(AdminCapability.OPERATIONS) ? stationRepository.findAll().stream()
                .map(item -> map("id", item.getId(), "name", item.getName(), "city", item.getCity(), "status", item.getStatus(),
                        "availability", item.getAvailability(), "hostAccountId", item.getHostUserId(),
                        "supplierCompanyId", item.getSupplierCompanyId(), "queue", item.getQueueCount(),
                        "occupancy", item.getOccupancyPercent())).toList() : List.of();
        List<Map<String, Object>> installations = caps.contains(AdminCapability.OPERATIONS) ? installationRepository.findAll().stream()
                .map(item -> map("id", item.getId(), "property", item.getProperty().getTitle(), "company", item.getCompany().getCompanyName(),
                        "product", item.getProduct().getModelName(), "status", item.getStatus(), "stationId", item.getStationId(),
                        "updatedAt", item.getUpdatedAt())).toList() : List.of();
        List<Booking> visibleBookings = caps.contains(AdminCapability.SUPPORT)
                || caps.contains(AdminCapability.OPERATIONS) || caps.contains(AdminCapability.FINANCE)
                ? bookingRepository.findAll() : List.of();
        Map<Long, Booking> bookingsById = visibleBookings.stream()
                .collect(java.util.stream.Collectors.toMap(Booking::getId, item -> item));
        List<Map<String, Object>> bookings = !visibleBookings.isEmpty()
                ? visibleBookings.stream().map(item -> map("id", item.getId(), "userId", item.getUserId(),
                        "station", item.getStationName(), "startTime", item.getStartTime(), "amount", item.getTotalAmount(),
                        "status", item.getStatus(), "refund", item.getRefundAmount())).toList() : List.of();
        List<Map<String, Object>> payments = caps.contains(AdminCapability.FINANCE) ? paymentRepository.findAll().stream()
                .map(item -> {
                    Booking booking = item.getBookingId() == null ? null : bookingsById.get(item.getBookingId());
                    return map("id", item.getId(), "userId", item.getUserId(), "bookingId", item.getBookingId(),
                        "amount", item.getAmount(), "transaction", item.getGatewayTransactionId(), "status", item.getStatus(),
                        "station", booking == null ? "Unlinked payment" : booking.getStationName(),
                        "outletTier", booking == null ? null : booking.getOutletTierName(),
                        "timestamp", item.getTimestamp());
                }).toList() : List.of();
        List<Map<String, Object>> trips = caps.contains(AdminCapability.AI_NETWORK) ? tripRepository.findAll().stream()
                .map(item -> map("id", item.getId(), "userId", item.getUserId(), "origin", item.getOrigin(),
                        "destination", item.getDestination(), "purpose", item.getTripPurpose(), "mode", item.getAutonomyMode(),
                        "status", item.getStatus(), "budget", item.getMaximumChargingBudget(), "cost", item.getEstimatedChargingCost(),
                        "memory", item.getMemorySummary())).toList() : List.of();
        List<Map<String, Object>> network = caps.contains(AdminCapability.AI_NETWORK) ? experienceRepository.findAll().stream()
                .sorted(Comparator.comparing(item -> item.getCreatedAt(), Comparator.reverseOrder())).limit(100)
                .map(item -> map("id", item.getId(), "route", item.getOrigin() + " → " + item.getDestination(),
                        "stationId", item.getStationId(), "outcome", item.getOutcome(), "detail", item.getDetail(),
                        "delayMinutes", item.getDelayMinutes(), "rating", item.getRating(), "createdAt", item.getCreatedAt())).toList() : List.of();
        List<Map<String, Object>> announcements = caps.contains(AdminCapability.SUPPORT) ? announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(item -> map("id", item.getId(), "title", item.getTitle(), "message", item.getMessage(),
                        "audience", item.getAudience(), "severity", item.getSeverity(), "active", item.isActive(),
                        "createdAt", item.getCreatedAt())).toList() : List.of();
        return new AdminPortalSnapshot(metrics, accounts, companies, hosts, properties, products, stations,
                installations, bookings, payments, trips, network, announcements);
    }

    public List<AdminAuditLog> audits() {
        access.require(AdminCapability.AUDIT);
        return auditRepository.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional
    public CompanyVerificationResponse reviewCompany(Long companyId, CompanyVerificationReviewRequest request) {
        AdminAccount admin = access.require(AdminCapability.VERIFICATIONS);
        CompanyVerificationResponse response = companyVerificationService.review(companyId, admin.getAccountId(), request);
        audit(admin, "REVIEW_COMPANY", "COMPANY", companyId, request.status() + ": " + text(request.note()));
        return response;
    }

    @Transactional
    public void reviewHost(Long accountId, AdminReviewNoteRequest request) {
        AdminAccount admin = access.require(AdminCapability.VERIFICATIONS);
        HostProfile profile = hostRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Host profile not found"));
        Account account = profile.getAccount();
        if (request.approved()) {
            if (profile.getKycDocumentUrl() == null || profile.getKycDocumentUrl().isBlank()) {
                throw new BadRequestException("Host identity/ownership evidence is required before approval");
            }
            profile.setVerified(true);
            profile.setVerificationStatus(HostVerificationStatus.VERIFIED);
            if (account.getAccountType() == AccountType.INDIVIDUAL) account.getRoles().add(AccountRole.ROLE_HOST);
        } else {
            profile.setVerified(false);
            profile.setVerificationStatus(HostVerificationStatus.REJECTED);
        }
        hostRepository.save(profile);
        accountRepository.save(account);
        audit(admin, "REVIEW_HOST", "HOST", accountId, (request.approved() ? "APPROVED" : "REJECTED") + ": " + text(request.note()));
    }

    @Transactional
    public void reviewProperty(Long id, AdminReviewNoteRequest request) {
        AdminAccount admin = access.require(AdminCapability.VERIFICATIONS);
        var property = landRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        boolean hostVerified = hostRepository.findById(property.getHostUserId()).map(HostProfile::isVerified).orElse(false);
        if (request.approved() && (!hostVerified || property.getOwnershipDocumentUrl() == null || property.getOwnershipDocumentUrl().isBlank())) {
            throw new BadRequestException("Verified Host identity and ownership evidence are required before publishing this property");
        }
        property.setStatus(request.approved() ? LandListingStatus.APPROVED : LandListingStatus.REJECTED);
        property.setDiscoverable(request.approved());
        property.setAdminReviewNote(text(request.note()));
        landRepository.save(property);
        audit(admin, "REVIEW_PROPERTY", "PROPERTY", id, property.getStatus() + ": " + text(request.note()));
    }

    @Transactional
    public void reviewProduct(Long id, AdminReviewNoteRequest request) {
        AdminAccount admin = access.require(AdminCapability.VERIFICATIONS);
        var product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Charger product not found"));
        if (request.approved() && (!companyVerificationService.isMarketplaceVerified(product.getCompany())
                || product.getComplianceDocumentUrl() == null || product.getComplianceDocumentUrl().isBlank())) {
            throw new BadRequestException("Vidyut-verified company and product compliance evidence are required");
        }
        product.setApprovalStatus(request.approved() ? ProductApprovalStatus.APPROVED : ProductApprovalStatus.REJECTED);
        product.setActive(request.approved());
        product.setAdminReviewNote(text(request.note()));
        productRepository.save(product);
        audit(admin, "REVIEW_PRODUCT", "CHARGER_PRODUCT", id, product.getApprovalStatus() + ": " + text(request.note()));
    }

    @Transactional
    public void setAccountEnabled(Long id, boolean enabled) {
        AdminAccount admin = access.require(AdminCapability.ACCOUNTS);
        Account account = accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (account.getAccountType() == AccountType.ADMIN) throw new BadRequestException("Use Admin staff management for administrator accounts");
        account.setEnabled(enabled);
        accountRepository.save(account);
        audit(admin, enabled ? "ACTIVATE_ACCOUNT" : "SUSPEND_ACCOUNT", "ACCOUNT", id, account.getEmail());
    }

    @Transactional
    public void cancelBooking(Long id, String note) {
        AdminAccount admin = access.require(AdminCapability.SUPPORT);
        var booking = bookingRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getStatus() == BookingStatus.COMPLETED) throw new BadRequestException("Completed bookings cannot be cancelled");
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        audit(admin, "CANCEL_BOOKING", "BOOKING", id, text(note));
    }

    @Transactional
    public void refundPayment(Long id, String note) {
        AdminAccount admin = access.require(AdminCapability.FINANCE);
        var payment = paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getStatus() != PaymentStatus.SUCCESS) throw new BadRequestException("Only successful payments can be refunded");
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        audit(admin, "REFUND_PAYMENT", "PAYMENT", id, text(note));
    }

    @Transactional
    public Map<String, Object> createAnnouncement(AdminAnnouncementRequest request) {
        AdminAccount admin = access.require(AdminCapability.SUPPORT);
        AdminAnnouncement saved = announcementRepository.save(AdminAnnouncement.builder().title(request.title().trim())
                .message(request.message().trim()).audience(request.audience()).severity(request.severity())
                .createdByAdminId(admin.getAccountId()).build());
        audit(admin, "CREATE_ANNOUNCEMENT", "ANNOUNCEMENT", saved.getId(), saved.getTitle());
        return map("id", saved.getId(), "title", saved.getTitle(), "audience", saved.getAudience(), "severity", saved.getSeverity());
    }

    @Transactional
    public AdminProfileResponse createAdmin(AdminAccountCreateRequest request) {
        AdminAccount actor = access.currentAdmin();
        if (actor.getAdminRole() != AdminRole.SUPER_ADMIN) throw new com.vidyut.common.exception.ForbiddenException("Only a Super Admin can create staff accounts");
        String email = request.email().trim().toLowerCase();
        if (accountRepository.existsByEmailIgnoreCase(email)) throw new DuplicateResourceException("Account already exists with email: " + email);
        Account account = accountRepository.save(Account.builder().email(email).passwordHash(passwordEncoder.encode(request.password()))
                .accountType(AccountType.ADMIN).roles(new LinkedHashSet<>(Set.of(AccountRole.ROLE_ADMIN)))
                .enabled(true).emailVerified(true).build());
        AdminAccount admin = adminRepository.save(AdminAccount.builder().account(account).displayName(request.displayName().trim())
                .adminRole(request.role()).build());
        audit(actor, "CREATE_ADMIN", "ADMIN", account.getId(), email + " as " + request.role());
        return access.profile(admin);
    }

    @Transactional
    public AdminProfileResponse updateAdminRole(Long id, AdminRoleUpdateRequest request) {
        AdminAccount actor = access.currentAdmin();
        if (actor.getAdminRole() != AdminRole.SUPER_ADMIN) throw new com.vidyut.common.exception.ForbiddenException("Only a Super Admin can change staff roles");
        if (actor.getAccountId().equals(id)) throw new BadRequestException("A Super Admin cannot change their own role");
        AdminAccount target = adminRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Admin account not found"));
        target.setAdminRole(request.role());
        adminRepository.save(target);
        audit(actor, "CHANGE_ADMIN_ROLE", "ADMIN", id, request.role().name());
        return access.profile(target);
    }

    private void audit(AdminAccount admin, String action, String resource, Object id, String summary) {
        auditRepository.save(AdminAuditLog.builder().adminAccountId(admin.getAccountId()).action(action)
                .resourceType(resource).resourceId(id == null ? null : String.valueOf(id))
                .summary(summary == null || summary.isBlank() ? action : summary).build());
    }

    private String text(String value) { return value == null ? "" : value.trim(); }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
}
