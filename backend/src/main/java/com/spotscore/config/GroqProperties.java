package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Groq(https://console.groq.com)의 OpenAI 호환 Chat Completions API 설정. 무료
 * 티어를 제공해 이 프로젝트(포트폴리오 데모, 저트래픽)에 적합하다고 판단해
 * 2026-08 OpenAI에서 전환됨(CLAUDE.md 확정 기술 스택 참고) - 무료 티어는 분당/일일
 * 요청·토큰 한도가 있고, 호스팅하는 오픈소스 모델이 주기적으로 교체될 수 있어
 * model 값이 만료되면 https://console.groq.com/docs/models 에서 재확인 필요.
 *
 * maxToolIterations: 챗봇 tool-calling 루프의 최대 반복 횟수. 모델이 계속
 * tool_calls만 반환하는 이상 상황(무한루프)에서 비용 폭주를 막기 위한 상한이다
 * (ChatService 참고).
 */
@ConfigurationProperties(prefix = "spotscore.groq")
public record GroqProperties(
        String baseUrl,
        String apiKey,
        String model,
        Integer maxToolIterations,
        Integer maxTokens,
        Integer timeoutSeconds
) {
}
