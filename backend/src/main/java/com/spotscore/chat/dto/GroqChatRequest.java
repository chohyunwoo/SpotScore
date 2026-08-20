package com.spotscore.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroqChatRequest(
        String model,
        List<GroqMessage> messages,
        List<GroqTool> tools,
        @JsonProperty("tool_choice") String toolChoice,
        @JsonProperty("max_tokens") Integer maxTokens
) {
}
