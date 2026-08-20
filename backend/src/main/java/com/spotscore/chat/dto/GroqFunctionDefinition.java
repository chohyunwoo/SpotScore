package com.spotscore.chat.dto;

import com.fasterxml.jackson.databind.JsonNode;

// parameters는 JSON Schema 객체를 그대로 담는다 - 각 ChatTool 구현체가
// ObjectMapper로 직접 트리를 구성한다.
public record GroqFunctionDefinition(String name, String description, JsonNode parameters) {
}
