package com.spotscore.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security 6는 CSRF 토큰을 "지연 로딩"해서, 실제로 토큰 값을 읽기 전에는
 * XSRF-TOKEN 쿠키를 응답에 심지 않는다. SPA(fetch)는 이 쿠키 값을 읽어 다음
 * 변경 요청의 X-XSRF-TOKEN 헤더에 실어보내야 하므로, 매 요청마다 토큰을 한 번
 * 읽어(getToken()) 쿠키가 확실히 내려가도록 강제한다(Spring 공식 SPA 연동 가이드).
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken != null) {
            // 토큰 값을 실제로 읽어야 CookieCsrfTokenRepository가 응답에 쿠키를 쓴다.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
