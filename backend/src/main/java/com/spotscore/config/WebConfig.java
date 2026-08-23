package com.spotscore.config;

import com.spotscore.logging.RequestTimingInterceptor;
import com.spotscore.security.AdminApiKeyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 인터셉터 등록. 요청 타이밍 로깅은 전체 API에, 공유 API Key 인증은 admin 경로에만 건다.
 *
 * CORS는 여기(MVC 계층)가 아니라 SecurityConfig의 CorsConfigurationSource로 옮겼다 -
 * 즐겨찾기 세션 로그인 도입으로 세션/CSRF 쿠키를 교차 오리진으로 주고받아야 하는데,
 * 쿠키(allowCredentials)가 걸린 요청은 Spring Security 필터 체인의 CorsFilter가 먼저
 * 처리하기 때문. 허용 오리진 출처는 그대로 CorsProperties(profile별 설정)다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RequestTimingInterceptor requestTimingInterceptor;
    private final AdminApiKeyInterceptor adminApiKeyInterceptor;

    public WebConfig(RequestTimingInterceptor requestTimingInterceptor,
                      AdminApiKeyInterceptor adminApiKeyInterceptor) {
        this.requestTimingInterceptor = requestTimingInterceptor;
        this.adminApiKeyInterceptor = adminApiKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestTimingInterceptor).addPathPatterns("/api/v1/**");
        registry.addInterceptor(adminApiKeyInterceptor).addPathPatterns("/api/v1/admin/**");
    }
}
