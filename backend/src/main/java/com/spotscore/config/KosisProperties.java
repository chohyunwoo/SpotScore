package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * statYear: KOSIS DT_1B04005N도 SGIS stats/population.json과 마찬가지로 발행
 * 시차가 있을 수 있어 특정 연도를 코드에 못박지 않고 설정으로 둔다
 * (SgisProperties와 동일한 이유). 비워두면 KosisAgeCollector가 현재 연도-2를
 * 기본값으로 사용한다.
 */
@ConfigurationProperties(prefix = "spotscore.kosis")
public record KosisProperties(String baseUrl, String apiKey, Integer statYear) {
}
