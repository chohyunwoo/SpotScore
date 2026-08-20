package com.spotscore.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// arguments는 Groq(OpenAI 호환)가 raw JSON 문자열로 내려준다(중첩 객체가 아님) -
// 각 ChatTool이 스스로 파싱한다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqFunctionCall(String name, String arguments) {
}
