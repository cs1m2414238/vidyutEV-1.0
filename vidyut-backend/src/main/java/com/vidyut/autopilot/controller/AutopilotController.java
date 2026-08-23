package com.vidyut.autopilot.controller;

import com.vidyut.autopilot.dto.AutopilotProgressRequest;
import com.vidyut.autopilot.dto.AutopilotPlanResponse;
import com.vidyut.autopilot.dto.AutopilotTripRequest;
import com.vidyut.autopilot.dto.AutopilotTripResponse;
import com.vidyut.autopilot.dto.JourneyIntentParseRequest;
import com.vidyut.autopilot.dto.JourneyIntentParseResponse;
import com.vidyut.autopilot.dto.RouteExperienceRequest;
import com.vidyut.autopilot.dto.RouteExperienceResponse;
import com.vidyut.autopilot.dto.VehicleRecommendationRequest;
import com.vidyut.autopilot.dto.VehicleRecommendationResponse;
import com.vidyut.autopilot.service.AutopilotService;
import com.vidyut.autopilot.service.JourneyIntentParser;
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
    private final JourneyIntentParser journeyIntentParser;
    private final CurrentUserUtil currentUser;

    @PostMapping("/intent/parse")
    public ResponseEntity<ApiResponse<JourneyIntentParseResponse>> parseJourneyIntent(
            @Valid @RequestBody JourneyIntentParseRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Journey intent converted into enforceable fields",
                journeyIntentParser.parse(request.getText())));
    }

    @PostMapping("/vehicles/recommend")
    public ResponseEntity<ApiResponse<VehicleRecommendationResponse>> recommendVehicle(
            @Valid @RequestBody VehicleRecommendationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Owned vehicles compared against the live route",
                autopilotService.recommendVehicle(currentUser.getCurrentAccountId(), request)));
    }

    @PostMapping("/trips/preview")
    public ResponseEntity<ApiResponse<AutopilotPlanResponse>> previewTrip(
            @Valid @RequestBody AutopilotTripRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Autopilot proposal created without booking",
                autopilotService.previewTrip(currentUser.getCurrentAccountId(), request)));
    }

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

    @PostMapping("/trips/{tripId}/approve-reroute")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> approveReroute(@PathVariable Long tripId) {
        return ResponseEntity.ok(ApiResponse.success("Replacement charger approved and reserved",
                autopilotService.approvePreparedReroute(tripId, currentUser.getCurrentAccountId())));
    }

    @PostMapping("/trips/{tripId}/experience")
    public ResponseEntity<ApiResponse<RouteExperienceResponse>> recordExperience(
            @PathVariable Long tripId,
            @Valid @RequestBody RouteExperienceRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Route experience stored for future planning",
                autopilotService.recordExperience(tripId, currentUser.getCurrentAccountId(), request)));
    }
}
