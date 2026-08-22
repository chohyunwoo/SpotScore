package com.spotscore.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotscore.config.AdminSecurityProperties;
import com.spotscore.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /api/v1/admin/**은 로그인 시스템이 없는 내부 운영용 엔드포인트(배치 트리거,
 * 가중치 수정 등)라 세션 기반 인증 대신 공유 API Key 한 장으로 막는다 - "인증 없음"
 * 상태보다 확실히 나은 최소 조치. spotscore.admin.api-key가 비어 있으면(=미설정)
 * 요청을 전부 차단한다 - WebConfig가 CORS allowedOrigins 미설정 시 매핑 자체를
 * 등록하지 않아 사실상 막히는 것과 같은 fail-closed 원칙.
 */
@Component
public class AdminApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminApiKeyInterceptor.class);
    private static final String API_KEY_HEADER = "X-Admin-Api-Key";

    private final AdminSecurityProperties adminSecurityProperties;
    private final ObjectMapper objectMapper;

    public AdminApiKeyInterceptor(AdminSecurityProperties adminSecurityProperties, ObjectMapper objectMapper) {
        this.adminSecurityProperties = adminSecurityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!adminSecurityProperties.isConfigured()) {
            log.warn("spotscore.admin.api-key가 설정되지 않음 - admin 요청을 전부 차단함 - path: {}",
                    request.getRequestURI());
            reject(response, request, "관리자 API Key가 설정되지 않아 admin 엔드포인트가 비활성화되어 있습니다.");
            return false;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null || !constantTimeEquals(adminSecurityProperties.apiKey(), providedKey)) {
            log.warn("admin API 인증 실패 - path: {}, 헤더 존재 여부: {}", request.getRequestURI(), providedKey != null);
            reject(response, request, "관리자 인증에 실패했습니다.");
            return false;
        }

        return true;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, HttpServletRequest request, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", message, request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
