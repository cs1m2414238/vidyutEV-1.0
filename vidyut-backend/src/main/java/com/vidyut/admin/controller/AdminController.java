package com.vidyut.admin.controller;

import com.vidyut.admin.dto.AdminDashboardResponse;
import com.vidyut.admin.dto.CompanyApprovalRequest;
import com.vidyut.admin.service.AdminService;
import com.vidyut.auth.service.AuthService;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AuthService authService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboardStats() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDashboardStats()));
    }

    @PatchMapping("/companies/{id}/approval")
    public ResponseEntity<ApiResponse<String>> approveCompany(@PathVariable Long id, @RequestBody CompanyApprovalRequest request) {
        adminService.approveCompany(id, request);
        return ResponseEntity.ok(ApiResponse.success("Company approval status updated", null));
    }

    @PostMapping("/host-applications/{accountId}/approve")
    public ResponseEntity<ApiResponse<UserResponse>> approveHost(@PathVariable Long accountId) {
        return ResponseEntity.ok(ApiResponse.success("Host mode approved", authService.approveHost(accountId)));
    }
}
