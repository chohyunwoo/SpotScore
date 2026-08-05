package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * statYear: SGIS stats/population.json은 year 파라미터가 필수다 (실제 호출 시
 * "필수 파라미터 누락" 오류로 확인됨 - 최초 구현에는 빠져있었다). 통계는 발행에
 * 시차가 있어(실제 확인 결과 최신 연도 다음 해는 아직 미발행) 특정 연도를 코드에
 * 못박지 않고 설정으로 둔다. 비워두면 SgisCollector가 현재 연도-2를 기본값으로
 * 사용하되, 실제로는 이 값을 명시적으로 검증된 최신 발행 연도로 채워 쓸 것을
 * 권장한다.
 */
@ConfigurationProperties(prefix = "spotscore.sgis")
public record SgisProperties(String baseUrl, String consumerKey, String consumerSecret, Integer statYear) {
}
