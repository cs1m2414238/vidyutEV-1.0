package com.vidyut.routing.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.routing.dto.RoutePlanRequest;
import com.vidyut.routing.dto.RoutePlanResponse;
import com.vidyut.routing.service.RoutingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routing")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingService routingService;

    @PostMapping("/plan")
    public ResponseEntity<ApiResponse<RoutePlanResponse>> planRoute(@Valid @RequestBody RoutePlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Route planned successfully", routingService.planRoute(request)));
    }
}
