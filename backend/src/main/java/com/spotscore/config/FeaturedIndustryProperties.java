package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

/**
 * 추천 업종(featured=true) 시딩 대상 코드 목록. CLAUDE.md 확장성 원칙(업종 코드
 * 하드코딩 금지)에 따라 코드에 나열하지 않고 설정값으로 둔다 - 실제 업소 수
 * 상위 30개 집계 결과가 바뀌면 이 값만 바꾸면 된다.
 */
@ConfigurationProperties(prefix = "spotscore.industry")
public record FeaturedIndustryProperties(List<String> featuredCodes) {

    public FeaturedIndustryProperties {
        featuredCodes = featuredCodes == null
                ? List.of()
                : featuredCodes.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(code -> !code.isEmpty())
                        .toList();
    }
}
