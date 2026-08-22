package com.spotscore.config;

import com.spotscore.logging.RequestTimingInterceptor;
import com.spotscore.security.AdminApiKeyInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final CorsProperties corsProperties;
    private final RequestTimingInterceptor requestTimingInterceptor;
    private final AdminApiKeyInterceptor adminApiKeyInterceptor;

    public WebConfig(CorsProperties corsProperties, RequestTimingInterceptor requestTimingInterceptor,
                      AdminApiKeyInterceptor adminApiKeyInterceptor) {
        this.corsProperties = corsProperties;
        this.requestTimingInterceptor = requestTimingInterceptor;
        this.adminApiKeyInterceptor = adminApiKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestTimingInterceptor).addPathPatterns("/api/v1/**");
        registry.addInterceptor(adminApiKeyInterceptor).addPathPatterns("/api/v1/admin/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (corsProperties.allowedOrigins().isEmpty()) {
            log.warn("spotscore.cors.allowed-origins가 설정되지 않음 - 프론트에서의 요청이 CORS로 막힐 수 있음");
            return;
        }
        registry.addMapping("/api/v1/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
