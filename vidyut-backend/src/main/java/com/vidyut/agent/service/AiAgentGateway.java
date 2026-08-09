package com.vidyut.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.agent.dto.EvAgentChatRequest;
import com.vidyut.agent.dto.EvAgentChatResponse;
import com.vidyut.common.exception.AgentServiceUnavailableException;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ForbiddenException;
import com.vidyut.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AiAgentGateway {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiAgentGateway(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${vidyut.agent.base-url:http://localhost:8001}") String agentBaseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(agentBaseUrl).build();
        this.objectMapper = objectMapper;
    }

    public EvAgentChatResponse chat(String authorization, EvAgentChatRequest request) {
        try {
            EvAgentChatResponse response = restClient.post()
                    .uri("/v1/chat")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(request)
                    .retrieve()
                    .body(EvAgentChatResponse.class);
            if (response == null || response.getReply() == null) {
                throw new AgentServiceUnavailableException("The AI agent returned an invalid response");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw mapAgentError(exception);
        } catch (ResourceAccessException exception) {
            throw new AgentServiceUnavailableException(
                    "The Vidyut AI agent is offline. Start the Python service on port 8001."
            );
        }
    }

    private RuntimeException mapAgentError(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String message = errorMessage(exception.getResponseBodyAsString());
        if (status.value() == 401) return new UnauthorizedException(message);
        if (status.value() == 403) return new ForbiddenException(message);
        if (status.is4xxClientError()) return new BadRequestException(message);
        return new AgentServiceUnavailableException(message);
    }

    private String errorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode detail = root.path("detail");
            if (detail.isTextual() && !detail.asText().isBlank()) return detail.asText();
            JsonNode message = root.path("message");
            if (message.isTextual() && !message.asText().isBlank()) return message.asText();
        } catch (Exception ignored) {
            // Return a stable message without exposing transport or parsing internals.
        }
        return "The Vidyut AI agent request failed";
    }
}
