package com.vidyut.admin.dto;

import com.vidyut.company.dto.CompanyVerificationResponse;

import java.util.List;
import java.util.Map;

public record AdminPortalSnapshot(
        Map<String, Long> metrics,
        List<Map<String, Object>> accounts,
        List<CompanyVerificationResponse> companyVerifications,
        List<Map<String, Object>> hostVerifications,
        List<Map<String, Object>> properties,
        List<Map<String, Object>> products,
        List<Map<String, Object>> stations,
        List<Map<String, Object>> installations,
        List<Map<String, Object>> bookings,
        List<Map<String, Object>> payments,
        List<Map<String, Object>> autopilotTrips,
        List<Map<String, Object>> networkSuggestions,
        List<Map<String, Object>> announcements
) {}
