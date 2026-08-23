package com.vidyut.company.dto;

import com.vidyut.company.entity.MaintenancePriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompanyAgentActionRequest(
        @NotNull CompanyAgentActionType action,
        Long chargerId,
        Long stationId,
        Double proposedPricePerKwh,
        MaintenancePriority priority,
        @Size(max = 500) String reason,
        boolean approved
) {}
