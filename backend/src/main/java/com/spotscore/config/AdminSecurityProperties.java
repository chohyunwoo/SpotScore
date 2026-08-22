package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * /api/v1/admin/** 보호용 공유 API Key. SGIS/상권정보/KOSIS 키와 마찬가지로 dev는
 * 빈 기본값(.env 설정 필요), prod는 기본값 없이 필수(미설정 시 부팅 실패)로 둔다 -
 * "관리자 엔드포인트가 인증 없이 배포되는 사고"를 배포 시점에 막기 위함.
 */
@ConfigurationProperties(prefix = "spotscore.admin")
public record AdminSecurityProperties(String apiKey) {

    public AdminSecurityProperties {
        apiKey = apiKey == null ? "" : apiKey;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }
}
