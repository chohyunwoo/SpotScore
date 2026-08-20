package com.spotscore.chat;

import com.spotscore.chat.dto.GroqChatRequest;
import com.spotscore.chat.dto.GroqChatResponse;
import com.spotscore.chat.dto.GroqMessage;
import com.spotscore.chat.dto.GroqTool;
import com.spotscore.config.GroqProperties;
import com.spotscore.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * Groq의 OpenAI 호환 Chat Completions API 호출. SGIS/상권정보와 달리 실패를 HTTP
 * 상태코드로 표시하므로(WebClient의 retrieve()가 4xx/5xx에서 기본적으로 예외를 던짐),
 * "200 + 에러코드"를 걸러내는 1단계 매핑이 필요 없다 - transport 단계 매핑
 * (.onErrorMap)만으로 ExternalApiException을 씌운다.
 */
@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    // 실제 호출로 확인: 무료 티어 분당 토큰 한도(TPM)가 낮은 편이라(예: 8000
    // tokens/min) tool-calling 루프처럼 한 답변에 Groq를 여러 번 왕복 호출하면 429
    // Too Many Requests가 실제로 발생한다(2026-08 확인). StoreZoneCollector의 429
    // 재시도 패턴을 그대로 재사용 - 토큰 버킷은 짧은 주기로 다시 차므로(관측된
    // x-ratelimit-reset-tokens는 1초 미만) 짧은 지수 백오프로 대부분 복구된다.
    private static final int MAX_RETRY_ATTEMPTS = 4;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(1000);

    private final WebClient groqWebClient;
    private final GroqProperties properties;

    public GroqClient(@Qualifier("groqWebClient") WebClient groqWebClient, GroqProperties properties) {
        this.groqWebClient = groqWebClient;
        this.properties = properties;
    }

    public Mono<GroqChatResponse> chatCompletion(List<GroqMessage> messages, List<GroqTool> tools) {
        log.debug("Groq 챗 컴플리션 요청 시작 - model: {}, messageCount: {}, toolCount: {}",
                properties.model(), messages.size(), tools.size());
        GroqChatRequest body = new GroqChatRequest(properties.model(), messages, tools, "auto", properties.maxTokens());
        return groqWebClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(GroqChatResponse.class)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_BACKOFF)
                        .filter(ex -> ex instanceof WebClientResponseException.TooManyRequests)
                        .doBeforeRetry(signal -> log.warn(
                                "Groq 429 Too Many Requests - 재시도 {}회차, model: {}",
                                signal.totalRetriesInARow() + 1, properties.model())))
                .doOnNext(response -> log.info("Groq 응답 수신 - choiceCount: {}",
                        response.choices() == null ? 0 : response.choices().size()))
                .onErrorMap(ex -> !(ex instanceof ExternalApiException), ex -> {
                    log.error("Groq 챗 컴플리션 호출 중 예외 발생", ex);
                    return new ExternalApiException("GROQ", "챗 컴플리션 호출 실패: " + ex.getMessage(), ex);
                });
    }
}
