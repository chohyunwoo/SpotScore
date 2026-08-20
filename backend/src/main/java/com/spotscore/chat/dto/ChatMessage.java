package com.spotscore.chat.dto;

// 프론트와 주고받는 단순화된 메시지 형태. role은 "user"/"assistant"만 쓴다 - system/tool
// 메시지는 백엔드 내부(GroqMessage)에만 존재하고 프론트에는 노출하지 않는다.
public record ChatMessage(String role, String content) {
}
