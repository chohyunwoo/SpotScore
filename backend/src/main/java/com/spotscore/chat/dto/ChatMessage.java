package com.spotscore.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 프론트와 주고받는 단순화된 메시지 형태. role은 "user"/"assistant"만 쓴다 - system/tool
// 메시지는 백엔드 내부(GroqMessage)에만 존재하고 프론트에는 노출하지 않는다.
// 공개(무인증) 엔드포인트라 content 길이 상한을 둬 거대한 본문으로 인한 토큰/메모리
// 남용을 막는다(이슈 #32).
public record ChatMessage(
        @NotBlank(message = "메시지 role은 필수입니다.")
        String role,

        @NotBlank(message = "메시지 내용은 필수입니다.")
        @Size(max = 4000, message = "메시지 내용은 4000자 이하여야 합니다.")
        String content) {
}
