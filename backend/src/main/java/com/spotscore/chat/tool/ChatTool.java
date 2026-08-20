package com.spotscore.chat.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Groq(OpenAI 호환) function-calling tool 하나를 표현한다. execute()가 던지는 예외는
 * ToolRegistry.execute()가 잡아 에러 형태의 tool 결과로 변환하므로, 구현체는
 * 원인이 되는 예외(ResourceNotFoundException 등)를 억지로 삼킬 필요는 없다 -
 * 다만 "데이터 없음"을 일반 에러가 아니라 의미 있는 tool 결과로 표현하고 싶은
 * 경우(RegionDetailTool 등)는 구현체가 직접 catch해서 처리한다.
 */
public interface ChatTool {

    String name();

    String description();

    /** Groq의 function.parameters에 그대로 실리는 JSON Schema 객체. */
    JsonNode parametersSchema();

    /** argumentsJson은 Groq가 내려준 raw JSON 문자열(예: {"industryCode":"I201"}). */
    String execute(String argumentsJson) throws Exception;
}
