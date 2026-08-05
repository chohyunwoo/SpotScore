package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 허용할 프론트 오리진 목록. CLAUDE.md 환경 분리 원칙에 따라 dev(Vite 개발 서버
 * http://localhost:5173)/prod 값을 코드에 고정하지 않고 profile별 설정으로 둔다.
 */
@ConfigurationProperties(prefix = "spotscore.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
    }
}
