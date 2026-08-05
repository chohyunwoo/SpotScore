package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서울 전체 행정동 발견/매핑 검증(SeoulRegionDiscoveryService) 설정.
 *
 * rootAdmCd: MVP 범위(서울) 루트 코드. CLAUDE.md 확장성 원칙("서울만" 제약은
 * 조회 조건/설정값으로 처리, 코드에 지역명 하드코딩 금지)에 따라 서비스 로직에
 * "11"을 박아넣지 않고 설정값으로 둔다 - 전국 대응 시 이 값만 바꾸면 된다.
 *
 * signguSamplePages: 상권정보 API에는 "구 목록 전체 조회" 오퍼레이션이 없어,
 * divId=ctprvnCd로 시도 전체를 조회하며 등장하는 signguCd를 표본으로 수집한다.
 * 페이지 수를 늘릴수록 커버리지는 높아지지만 호출 비용도 늘어난다.
 *
 * requestIntervalMillis: 실제로 서울 전체(426개 동) 라이트 매핑 검증을 페이싱 없이
 * 돌렸더니 상권정보 API가 곧바로 "429 Too Many Requests"를 반환했다 - 짧은 시간에
 * 너무 많은 요청을 보낸 탓이다. 이 값만큼 매 호출 사이에 간격을 둔다.
 */
@ConfigurationProperties(prefix = "spotscore.discovery")
public record DiscoveryProperties(String rootAdmCd, Integer signguSamplePages, Integer requestIntervalMillis) {

    public DiscoveryProperties {
        if (signguSamplePages == null || signguSamplePages <= 0) {
            signguSamplePages = 20;
        }
        if (requestIntervalMillis == null || requestIntervalMillis < 0) {
            requestIntervalMillis = 300;
        }
    }
}
