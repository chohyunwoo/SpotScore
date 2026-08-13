package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

/**
 * 업종 대분류 접두어 기준 ageScore 방향성 시딩 설정. CLAUDE.md 확장성 원칙
 * (업종 코드 하드코딩 금지)에 따라 코드에 나열하지 않고 설정값으로 둔다 -
 * industry_code가 이 접두어로 시작하면 해당 방향으로 시딩하고, 어느 목록에도
 * 없으면 NEUTRAL로 시딩한다(IndustryAgeDirectionSeedingService).
 */
@ConfigurationProperties(prefix = "spotscore.industry.age-direction")
public record IndustryAgeDirectionProperties(List<String> positivePrefixes, List<String> negativePrefixes) {

    public IndustryAgeDirectionProperties {
        positivePrefixes = normalize(positivePrefixes);
        negativePrefixes = normalize(negativePrefixes);
    }

    private static List<String> normalize(List<String> prefixes) {
        return prefixes == null
                ? List.of()
                : prefixes.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(prefix -> !prefix.isEmpty())
                        .toList();
    }
}
