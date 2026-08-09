package com.vidyut.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvAgentChatRequest {

    @NotBlank(message = "Agent message is required")
    @Size(max = 4000, message = "Agent message must be 4000 characters or fewer")
    private String message;

    @Size(min = 8, max = 100, message = "Session ID must be between 8 and 100 characters")
    private String sessionId;

    @Size(min = 8, max = 100, message = "Request ID must be between 8 and 100 characters")
    private String requestId;
}
