package com.vidyut.session.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.session.dto.ChargingSessionResponse;
import com.vidyut.session.service.ChargingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ev/sessions")
@RequiredArgsConstructor
public class ChargingSessionController {
    private final ChargingSessionService sessionService;
    private final CurrentUserUtil currentUser;

    @PostMapping("/booking/{bookingId}/start")
    public ResponseEntity<ApiResponse<ChargingSessionResponse>> start(@PathVariable Long bookingId) {
        return ResponseEntity.ok(ApiResponse.success("Charging started",
                sessionService.start(currentUser.getCurrentAccountId(), bookingId)));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ChargingSessionResponse>>> active() {
        return ResponseEntity.ok(ApiResponse.success(sessionService.getActive(currentUser.getCurrentAccountId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChargingSessionResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(sessionService.get(currentUser.getCurrentAccountId(), id)));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<ApiResponse<ChargingSessionResponse>> stop(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Charging stopped",
                sessionService.stop(currentUser.getCurrentAccountId(), id)));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<ChargingSessionResponse>> pay(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Session paid",
                sessionService.pay(currentUser.getCurrentAccountId(), id)));
    }
}
