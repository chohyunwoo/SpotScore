package com.spotscore.security;

import com.spotscore.config.CorsProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 즐겨찾기(사용자별 리소스)를 위한 세션 기반 로그인 보안 설정. 설계 원칙:
 *
 * - 공개 데이터 조회(랭킹/상세/업종/가중치/업소/챗봇)는 로그인 없이 그대로 열어둔다
 *   (anyRequest().permitAll()). 인증이 필요한 건 /api/v1/favorites/**와 내 정보 조회뿐.
 * - /api/v1/admin/**은 기존 AdminApiKeyInterceptor(공유 API Key)가 계속 담당한다.
 *   Spring Security는 이 경로를 permitAll로 통과시키고, MVC 인터셉터가 인증을 강제한다
 *   (세션 로그인과 admin API Key는 별개 체계 - CLAUDE.md).
 * - CSRF: 세션 쿠키 기반이라 CSRF 방어가 필요하다. SPA가 읽을 수 있게 HttpOnly=false
 *   쿠키(XSRF-TOKEN)로 토큰을 내리고, 상태를 바꾸는 즐겨찾기/로그아웃 요청에만 적용한다.
 *   admin(별도 키 인증)·챗봇(공개)·로그인/회원가입(세션 부트스트랩 지점)은 CSRF 제외.
 * - 쿠키 SameSite/Secure: 배포(prod)는 프론트(Cloudflare)와 백엔드(Render)가 서로 다른
 *   도메인이라 세션/CSRF 쿠키가 "교차 사이트"로 오간다. 이 경우 브라우저는 SameSite=None;
 *   Secure가 아니면 쿠키를 전송하지 않으므로 prod에선 그 값을 쓴다. 세션 쿠키(JSESSIONID)는
 *   application-prod.yml의 server.servlet.session.cookie 설정이 담당하고, 여기선 그 값과
 *   동일하게 CSRF 쿠키(XSRF-TOKEN)에도 SameSite/Secure를 맞춰 둘이 어긋나지 않게 한다.
 *   dev(둘 다 localhost = 동일 사이트)는 기본값 Lax/비-secure라 http에서도 정상 동작한다.
 */
@Configuration
public class SecurityConfig {

    private final CorsProperties corsProperties;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    // 세션 쿠키(server.servlet.session.cookie.*)와 같은 값을 재사용해 CSRF 쿠키도 맞춘다.
    // 기본값은 dev(로컬 http)용 - prod yml에서 none/true로 override한다.
    @Value("${server.servlet.session.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean cookieSecure;

    public SecurityConfig(CorsProperties corsProperties, RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.corsProperties = corsProperties;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 쿠키의 raw 토큰 값을 그대로 헤더 값으로 검증하도록 plain 핸들러를 쓴다
        // (기본 Xor 핸들러는 마스킹이 있어 JS가 쿠키 값을 그대로 헤더에 넣으면 불일치).
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        // 교차 사이트 배포(prod)에서 XSRF-TOKEN 쿠키가 전송되려면 세션 쿠키와 동일하게
        // SameSite=None; Secure여야 한다(dev는 Lax/비-secure 기본값).
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite(cookieSameSite).secure(cookieSecure));

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers(
                                "/api/v1/admin/**",
                                "/api/v1/chat",
                                "/api/v1/auth/login",
                                "/api/v1/auth/register"))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                // 세션은 필요할 때(로그인 시)만 생성한다. 공개 조회는 세션을 만들지 않는다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/favorites/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true))
                // SPA는 JSON API만 쓰므로 폼 로그인/HTTP Basic 기본 UI는 끈다
                // (로그인은 AuthController가 AuthenticationManager로 직접 처리).
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * CORS 설정. 기존 WebConfig가 MVC 계층에서 하던 것을 Security 필터 체인으로 옮긴다
     * (세션 쿠키를 주고받으려면 allowCredentials(true)가 필요하고, 쿠키/인증이 걸린
     * 요청은 Security의 CorsFilter가 먼저 처리하기 때문). 허용 오리진은 CorsProperties
     * (profile별 설정)에서 읽어 하드코딩하지 않는다 - 비어 있으면 CORS 허용을 등록하지
     * 않아 사실상 막힌다(fail-closed, 기존 WebConfig와 동일 원칙).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (corsProperties.allowedOrigins().isEmpty()) {
            return source;
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 세션(JSESSIONID)/CSRF 쿠키를 교차 오리진으로 주고받기 위해 필수.
        config.setAllowCredentials(true);
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
