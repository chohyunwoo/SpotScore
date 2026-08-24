package com.spotscore.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotscore.config.ChatRateLimitProperties;
import com.spotscore.exception.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 공개(무인증) 챗봇 엔드포인트(POST /api/v1/chat)에 IP당 요청 레이트리밋을 건다.
 *
 * <p>왜: /api/v1/chat은 permitAll·무인증이고 요청 1건이 Groq를 여러 번 왕복한다. 익명
 * 반복 호출로 Groq 무료 티어 쿼터가 소진되면 모든 사용자에게 챗봇이 중단되고, block
 * 스레드도 점유된다(OWASP API4:2023 Unrestricted Resource Consumption, 이슈 #32).
 * Cloudflare 유료 규칙 없이 in-code 토큰 버킷(bucket4j)으로 무료로 막는다.
 *
 * <p>IP별 버킷은 메모리(ConcurrentHashMap)에 둔다 - 단일 인스턴스 데모라 분산 저장소가
 * 불필요하다. 인스턴스 재시작 시 초기화돼도 무방(레이트리밋은 순간 폭주 방어가 목적).
 */
@Component
public class ChatRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ChatRateLimitFilter.class);
    private static final String CHAT_PATH = "/api/v1/chat";

    private final ChatRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> bucketsByIp = new ConcurrentHashMap<>();

    public ChatRateLimitFilter(ChatRateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 챗봇 POST 요청에만 적용한다(다른 경로/메서드는 통과).
        return !("POST".equalsIgnoreCase(request.getMethod()) && CHAT_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        Bucket bucket = bucketsByIp.computeIfAbsent(clientIp, ip -> newBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("챗봇 레이트리밋 초과 - clientIp: {}, 한도: {}회/{}초",
                clientIp, properties.capacity(), properties.refillPeriodSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(), "RATE_LIMITED",
                "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.", request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private Bucket newBucket() {
        // refillPeriodSeconds마다 capacity만큼 부드럽게(greedy) 다시 채우는 토큰 버킷.
        Refill refill = Refill.greedy(properties.capacity(), Duration.ofSeconds(properties.refillPeriodSeconds()));
        Bandwidth limit = Bandwidth.classic(properties.capacity(), refill);
        return Bucket.builder().addLimit(limit).build();
    }

    // 프론트가 Cloudflare Pages Function 리버스 프록시를 거치므로, 실제 클라이언트 IP는
    // X-Forwarded-For의 첫 번째 값에 담긴다. 없으면 원격 주소로 폴백한다.
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
