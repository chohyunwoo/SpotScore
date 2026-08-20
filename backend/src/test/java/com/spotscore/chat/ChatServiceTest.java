package com.spotscore.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotscore.chat.dto.ChatMessage;
import com.spotscore.chat.dto.ChatRequest;
import com.spotscore.chat.dto.ChatResponse;
import com.spotscore.chat.dto.GroqChatResponse;
import com.spotscore.chat.dto.GroqFunctionCall;
import com.spotscore.chat.dto.GroqMessage;
import com.spotscore.chat.dto.GroqToolCall;
import com.spotscore.chat.tool.FeaturedIndustriesTool;
import com.spotscore.chat.tool.FindRegionByNameTool;
import com.spotscore.chat.tool.RankingByIndustryTool;
import com.spotscore.chat.tool.RegionDetailTool;
import com.spotscore.chat.tool.ToolRegistry;
import com.spotscore.config.GroqProperties;
import com.spotscore.dto.RankingItem;
import com.spotscore.exception.ResourceNotFoundException;
import com.spotscore.query.IndustryQueryService;
import com.spotscore.query.ScoreQueryService;
import com.spotscore.scoring.AttractivenessTier;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatService의 tool-calling 루프를 검증한다. GroqClient만 mock하고 ToolRegistry는
 * 실제 tool 구현체(ScoreQueryService/IndustryQueryService는 mock)로 구성해, "Groq가
 * 내려준 JSON arguments를 파싱해 실제 쿼리 서비스 호출까지 이어지는 경로" 자체를 검증한다.
 */
class ChatServiceTest {

    private final ScoreQueryService scoreQueryService = mock(ScoreQueryService.class);
    private final IndustryQueryService industryQueryService = mock(IndustryQueryService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GroqClient groqClient = mock(GroqClient.class);

    private final ToolRegistry toolRegistry = new ToolRegistry(List.of(
            new FeaturedIndustriesTool(industryQueryService, objectMapper),
            new RankingByIndustryTool(scoreQueryService, objectMapper),
            new RegionDetailTool(scoreQueryService, objectMapper),
            new FindRegionByNameTool(scoreQueryService, objectMapper)
    ));

    private ChatService newChatService(int maxToolIterations) {
        GroqProperties properties = new GroqProperties(
                "https://api.groq.com/openai/v1", "test-api-key", "openai/gpt-oss-120b", maxToolIterations, 600, 30);
        return new ChatService(groqClient, toolRegistry, properties);
    }

    private static GroqChatResponse toolCallResponse(String toolName, String argumentsJson) {
        GroqToolCall call = new GroqToolCall("call_1", "function", new GroqFunctionCall(toolName, argumentsJson));
        GroqMessage message = new GroqMessage("assistant", null, List.of(call), null);
        return new GroqChatResponse(List.of(new GroqChatResponse.Choice(message, "tool_calls")));
    }

    private static GroqChatResponse finalAnswerResponse(String content) {
        GroqMessage message = new GroqMessage("assistant", content, null, null);
        return new GroqChatResponse(List.of(new GroqChatResponse.Choice(message, "stop")));
    }

    @Test
    void executesToolCallThenReturnsFinalAnswer() {
        when(scoreQueryService.getRanking("I201")).thenReturn(List.of(
                new RankingItem("11680640", "역삼1동", BigDecimal.valueOf(91.2), BigDecimal.valueOf(30),
                        BigDecimal.valueOf(10), BigDecimal.valueOf(51.2), 37.5, 127.0, 5.0, AttractivenessTier.ATTRACTIVE)
        ));
        when(groqClient.chatCompletion(any(), any()))
                .thenReturn(Mono.just(toolCallResponse("get_ranking_by_industry", "{\"industryCode\":\"I201\"}")))
                .thenReturn(Mono.just(finalAnswerResponse("역삼1동이 1위입니다")));

        ChatService chatService = newChatService(4);
        ChatResponse response = chatService.handle(
                new ChatRequest(List.of(new ChatMessage("user", "강남구에서 카페 어디가 좋아?")), null, null));

        assertThat(response.reply()).isEqualTo("역삼1동이 1위입니다");
        verify(scoreQueryService).getRanking("I201");
    }

    @Test
    void resourceNotFoundIsSwallowedByToolAndSurfacedAsNoDataResult() {
        when(scoreQueryService.getDetail("99999999", "I201"))
                .thenThrow(new ResourceNotFoundException("해당 지역×업종 조합의 점수 데이터가 없습니다"));
        when(groqClient.chatCompletion(any(), any()))
                .thenReturn(Mono.just(toolCallResponse("get_region_detail",
                        "{\"regionCode\":\"99999999\",\"industryCode\":\"I201\"}")))
                .thenReturn(Mono.just(finalAnswerResponse("해당 지역은 데이터가 없습니다")));

        ChatService chatService = newChatService(4);
        ChatResponse response = chatService.handle(
                new ChatRequest(List.of(new ChatMessage("user", "이상한동네 카페 어때?")), null, null));

        assertThat(response.reply()).isEqualTo("해당 지역은 데이터가 없습니다");
    }

    @Test
    void forcesFinalAnswerUsingGatheredToolResultsWhenIterationsExhausted() {
        when(scoreQueryService.getRanking("I201")).thenReturn(List.of(
                new RankingItem("11680640", "역삼1동", BigDecimal.valueOf(91.2), BigDecimal.valueOf(30),
                        BigDecimal.valueOf(10), BigDecimal.valueOf(51.2), 37.5, 127.0, 5.0, AttractivenessTier.ATTRACTIVE)
        ));
        when(groqClient.chatCompletion(any(), any()))
                .thenReturn(Mono.just(toolCallResponse("get_ranking_by_industry", "{\"industryCode\":\"I201\"}")))
                .thenReturn(Mono.just(finalAnswerResponse("모아둔 정보로 보면 역삼1동을 추천합니다")));

        ChatService chatService = newChatService(1);
        ChatResponse response = chatService.handle(
                new ChatRequest(List.of(new ChatMessage("user", "한식 창업 어디가 좋아?")), null, null));

        // 1회 tool 호출 이후 반복 한도(1)를 다 썼지만, 포기하는 대신 tool을 뺀 강제
        // 최종 호출을 한 번 더 해서 이미 조회해둔 랭킹 데이터를 답에 반영해야 한다.
        verify(groqClient, times(2)).chatCompletion(any(), any());
        assertThat(response.reply()).isEqualTo("모아둔 정보로 보면 역삼1동을 추천합니다");
    }

    @Test
    void fallsBackToApologyOnlyWhenForcedFinalCallStillReturnsNoContent() {
        when(industryQueryService.getIndustries(false)).thenReturn(List.of());
        when(groqClient.chatCompletion(any(), any()))
                .thenReturn(Mono.just(toolCallResponse("get_featured_industries", "{}")));

        ChatService chatService = newChatService(3);
        ChatResponse response = chatService.handle(
                new ChatRequest(List.of(new ChatMessage("user", "아무 질문")), null, null));

        // 반복 한도(3) + 강제 최종 호출 1회 = 총 4회. mock이 매번 tool_calls만 반환하는
        // 비현실적인 케이스라(강제 호출에서도 content가 null) 그제서야 사과 문구로 폴백한다.
        verify(groqClient, times(4)).chatCompletion(any(), any());
        assertThat(response.reply()).contains("질문을 조금 더 구체적으로");
    }
}
