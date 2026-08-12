package com.vidyut.admin.controller;

import com.vidyut.admin.dto.*;
import com.vidyut.admin.entity.AdminAuditLog;
import com.vidyut.admin.service.AdminPortalService;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.company.dto.CompanyVerificationResponse;
import com.vidyut.company.dto.CompanyVerificationReviewRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/portal")
@RequiredArgsConstructor
public class AdminPortalController {
    private final AdminPortalService portalService;

    @GetMapping("/snapshot")
    public ResponseEntity<ApiResponse<AdminPortalSnapshot>> snapshot() {
        return ResponseEntity.ok(ApiResponse.success(portalService.snapshot()));
    }

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<List<AdminAuditLog>>> audit() {
        return ResponseEntity.ok(ApiResponse.success(portalService.audits()));
    }

    @PatchMapping("/companies/{companyId}/review")
    public ResponseEntity<ApiResponse<CompanyVerificationResponse>> reviewCompany(
            @PathVariable Long companyId, @Valid @RequestBody CompanyVerificationReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company review saved", portalService.reviewCompany(companyId, request)));
    }

    @PatchMapping("/hosts/{accountId}/review")
    public ResponseEntity<ApiResponse<Void>> reviewHost(@PathVariable Long accountId,
                                                         @Valid @RequestBody AdminReviewNoteRequest request) {
        portalService.reviewHost(accountId, request);
        return ResponseEntity.ok(ApiResponse.success("Host review saved", null));
    }

    @PatchMapping("/properties/{id}/review")
    public ResponseEntity<ApiResponse<Void>> reviewProperty(@PathVariable Long id,
                                                             @Valid @RequestBody AdminReviewNoteRequest request) {
        portalService.reviewProperty(id, request);
        return ResponseEntity.ok(ApiResponse.success("Property review saved", null));
    }

    @PatchMapping("/products/{id}/review")
    public ResponseEntity<ApiResponse<Void>> reviewProduct(@PathVariable Long id,
                                                            @Valid @RequestBody AdminReviewNoteRequest request) {
        portalService.reviewProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product review saved", null));
    }

    @PatchMapping("/accounts/{id}/enabled")
    public ResponseEntity<ApiResponse<Void>> setAccountEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        portalService.setAccountEnabled(id, enabled);
        return ResponseEntity.ok(ApiResponse.success("Account access updated", null));
    }

    @PatchMapping("/bookings/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelBooking(@PathVariable Long id,
                                                            @RequestBody(required = false) Map<String, String> body) {
        portalService.cancelBooking(id, body == null ? null : body.get("note"));
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled", null));
    }

    @PatchMapping("/payments/{id}/refund")
    public ResponseEntity<ApiResponse<Void>> refundPayment(@PathVariable Long id,
                                                            @RequestBody(required = false) Map<String, String> body) {
        portalService.refundPayment(id, body == null ? null : body.get("note"));
        return ResponseEntity.ok(ApiResponse.success("Payment refunded", null));
    }

    @PostMapping("/announcements")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAnnouncement(
            @Valid @RequestBody AdminAnnouncementRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Announcement published", portalService.createAnnouncement(request)));
    }

    @PostMapping("/admins")
    public ResponseEntity<ApiResponse<AdminProfileResponse>> createAdmin(@Valid @RequestBody AdminAccountCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Administrator created", portalService.createAdmin(request)));
    }

    @PatchMapping("/admins/{id}/role")
    public ResponseEntity<ApiResponse<AdminProfileResponse>> updateAdminRole(@PathVariable Long id,
                                                                              @Valid @RequestBody AdminRoleUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Administrator role updated", portalService.updateAdminRole(id, request)));
    }
}
