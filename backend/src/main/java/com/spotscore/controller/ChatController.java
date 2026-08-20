package com.spotscore.controller;

import com.spotscore.chat.ChatService;
import com.spotscore.chat.dto.ChatRequest;
import com.spotscore.chat.dto.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "Groq tool-calling 기반 창업 상담 챗봇")
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "챗봇에 메시지 전송",
            description = "서버는 대화 상태를 저장하지 않는다 - 매 요청마다 프론트가 전체 이력을 함께 보내야 한다.")
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        int messageCount = request.messages() == null ? 0 : request.messages().size();
        log.info("요청 수신 - endpoint: {}, messageCount: {}", httpRequest.getRequestURI(), messageCount);
        return chatService.handle(request);
    }
}
