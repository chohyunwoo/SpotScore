package com.spotscore.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotscore.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증이 필요한 엔드포인트(/api/v1/favorites/** 등)에 로그인 없이 접근하면 Spring
 * Security 기본 동작(로그인 페이지로 302 리다이렉트)이 SPA에는 맞지 않는다. 여기서
 * AdminApiKeyInterceptor와 동일한 ErrorResponse(JSON) 포맷의 401을 내려, 프론트가
 * "로그인 필요"를 일관되게 처리할 수 있게 한다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("인증되지 않은 요청 - path: {}", request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", "로그인이 필요합니다.", request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
