package com.spotscore.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqToolCall(String id, String type, GroqFunctionCall function) {
}
