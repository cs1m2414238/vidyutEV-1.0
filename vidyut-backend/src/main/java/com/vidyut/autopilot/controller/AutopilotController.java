package com.vidyut.autopilot.controller;

import com.vidyut.autopilot.dto.AutopilotProgressRequest;
import com.vidyut.autopilot.dto.AutopilotTripRequest;
import com.vidyut.autopilot.dto.AutopilotTripResponse;
import com.vidyut.autopilot.service.AutopilotService;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ev/autopilot")
@RequiredArgsConstructor
public class AutopilotController {

    private final AutopilotService autopilotService;
    private final CurrentUserUtil currentUser;

    @PostMapping("/trips")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> launchTrip(
            @Valid @RequestBody AutopilotTripRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Autopilot planned and reserved the trip",
                autopilotService.launchTrip(currentUser.getCurrentAccountId(), request)));
    }

    @GetMapping("/trips/current")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> getCurrentTrip() {
        return ResponseEntity.ok(ApiResponse.success(
                autopilotService.getCurrentTrip(currentUser.getCurrentAccountId())));
    }

    @GetMapping("/trips/{tripId}")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> getTrip(@PathVariable Long tripId) {
        return ResponseEntity.ok(ApiResponse.success(
                autopilotService.getTrip(tripId, currentUser.getCurrentAccountId())));
    }

    @PostMapping("/trips/{tripId}/start")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> startJourney(
            @PathVariable Long tripId,
            @Valid @RequestBody(required = false) AutopilotProgressRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Journey monitoring started",
                autopilotService.startJourney(tripId, currentUser.getCurrentAccountId(), request)));
    }

    @PostMapping("/trips/{tripId}/simulate-fault")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> simulateFault(@PathVariable Long tripId) {
        return ResponseEntity.ok(ApiResponse.success("Fault handled and route rebooked",
                autopilotService.simulateChargerFault(tripId, currentUser.getCurrentAccountId())));
    }

    @PostMapping("/trips/{tripId}/complete-charging")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> completeCharging(@PathVariable Long tripId) {
        return ResponseEntity.ok(ApiResponse.success("Charging completion processed",
                autopilotService.completeCharging(tripId, currentUser.getCurrentAccountId())));
    }
}
