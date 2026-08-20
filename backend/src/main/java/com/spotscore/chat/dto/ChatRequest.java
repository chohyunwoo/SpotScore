package com.spotscore.chat.dto;

import java.util.List;

// industryCode/regionCode는 프론트 SelectionContext에서 온 참고용 힌트다 - 사용자가
// 실제로 다른 지역/업종을 물어보면 그쪽을 따라야 하므로 서버가 강제하지 않는다.
public record ChatRequest(List<ChatMessage> messages, String industryCode, String regionCode) {
}
