package com.vidyut.routing.service;

import com.vidyut.routing.dto.RoutePlanRequest;
import com.vidyut.routing.dto.RoutePlanResponse;

public interface RoutingService {
    RoutePlanResponse planRoute(RoutePlanRequest request);
}
