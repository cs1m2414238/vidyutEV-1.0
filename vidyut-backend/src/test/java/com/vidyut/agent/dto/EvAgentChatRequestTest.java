package com.vidyut.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.autopilot.dto.AutopilotTripRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvAgentChatRequestTest {

    @Test
    void forwardsStructuredTripContextToThePythonAgent() throws Exception {
        EvAgentChatRequest request = EvAgentChatRequest.builder()
                .message("Plan this trip")
                .requestId("request-12345678")
                .tripContext(AutopilotTripRequest.builder()
                        .vehicleId(42L)
                        .origin("Ujjain")
                        .destination("Bhopal")
                        .currentBatteryPercent(80)
                        .minimumArrivalBatteryPercent(15)
                        .maximumChargingBudget(500)
                        .build())
                .build();

        JsonNode json = new ObjectMapper().valueToTree(request);

        assertThat(json.path("tripContext").path("vehicleId").asLong()).isEqualTo(42L);
        assertThat(json.path("tripContext").path("origin").asText()).isEqualTo("Ujjain");
        assertThat(json.path("tripContext").path("destination").asText()).isEqualTo("Bhopal");
        assertThat(json.path("tripContext").path("maximumChargingBudget").asDouble()).isEqualTo(500);
    }
}
