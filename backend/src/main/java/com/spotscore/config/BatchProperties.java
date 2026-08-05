package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

/**
 * 배치 대상 지역 목록. CLAUDE.md 확장성 원칙(지역 하드코딩 금지)에 따라 코드에
 * 지역을 고정하지 않고 설정(application.yml/환경변수)에서 주입한다.
 *
 * 각 항목은 "sgisAdmCd:adongCd" 형식이다 - 실제 라이브 호출로 대조한 결과 SGIS
 * adm_cd와 상권정보 adongCd는 같은 지역이어도 서로 다른 번호체계라(TargetRegion,
 * Region.sgisAdmCd 참고) 하나의 코드를 양쪽에 그대로 쓸 수 없다. 예:
 * "11230640:11680640" (강남구 역삼1동, 실제 두 API로 검증된 값).
 *
 * requestIntervalMillis: 지역 수가 많아지면(서울 전체 등) 지역마다 상권정보 API를
 * 연달아 호출하게 되는데, 페이싱 없이 돌렸을 때 실제로 "429 Too Many Requests"를
 * 받은 적이 있어(SeoulRegionDiscoveryService에서 먼저 발견) 배치에도 안전장치로
 * 지역 처리 사이에 간격을 둔다.
 */
@ConfigurationProperties(prefix = "spotscore.batch")
public record BatchProperties(String cron, List<String> targetRegions, Integer requestIntervalMillis) {

    public BatchProperties {
        targetRegions = targetRegions == null
                ? List.of()
                : targetRegions.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(code -> !code.isEmpty())
                        .toList();
        if (requestIntervalMillis == null || requestIntervalMillis < 0) {
            requestIntervalMillis = 200;
        }
    }
}
