package com.spotscore.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// industryCode/regionCode는 프론트 SelectionContext에서 온 참고용 힌트다 - 사용자가
// 실제로 다른 지역/업종을 물어보면 그쪽을 따라야 하므로 서버가 강제하지 않는다.
// messages는 공개(무인증) 엔드포인트라 개수 상한을 둬, 거대한 이력으로 인한 토큰/스레드
// 남용(요청당 최대 maxToolIterations회 Groq 왕복)을 막는다(이슈 #32). @Valid로 각
// 메시지의 content 길이 제한(ChatMessage)까지 함께 검증한다.
public record ChatRequest(
        @NotEmpty(message = "messages는 최소 1개 이상이어야 합니다.")
        @Size(max = 30, message = "messages는 30개 이하여야 합니다.")
        List<@Valid ChatMessage> messages,

        String industryCode,
        String regionCode) {
}
