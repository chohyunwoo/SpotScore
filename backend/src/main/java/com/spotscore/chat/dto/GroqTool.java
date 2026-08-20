package com.spotscore.chat.dto;

public record GroqTool(String type, GroqFunctionDefinition function) {

    public static GroqTool function(GroqFunctionDefinition definition) {
        return new GroqTool("function", definition);
    }
}
