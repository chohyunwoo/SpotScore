package com.spotscore.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Groq의 /openai/v1/chat/completions는 OpenAI Chat Completions API와 동일한 wire
// 포맷을 그대로 따른다(OpenAI 호환 엔드포인트) - message 하나를 표현한다. 요청/응답
// 양쪽에 동일한 record를 재사용한다 - system/user 메시지는 content만, assistant의
// tool 호출 메시지는 content가 null이고 toolCalls만 채워지며, tool 결과 메시지는
// content+toolCallId만 채워진다. null 필드는 직렬화하지 않는다.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroqMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<GroqToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId
) {

    public static GroqMessage system(String content) {
        return new GroqMessage("system", content, null, null);
    }

    public static GroqMessage user(String content) {
        return new GroqMessage("user", content, null, null);
    }

    public static GroqMessage toolResult(String toolCallId, String content) {
        return new GroqMessage("tool", content, null, toolCallId);
    }
}
