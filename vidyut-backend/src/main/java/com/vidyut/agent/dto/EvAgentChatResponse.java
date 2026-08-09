package com.vidyut.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvAgentChatResponse {
    private String sessionId;
    private String requestId;
    private String reply;
    private String model;
    private List<AgentToolCallResponse> toolCalls;
}
