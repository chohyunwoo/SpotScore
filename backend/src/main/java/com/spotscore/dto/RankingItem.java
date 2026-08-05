package com.spotscore.dto;

import com.spotscore.scoring.AttractivenessTier;

import java.math.BigDecimal;

/**
 * GET /api/v1/scores/ranking 응답 항목. 필드명은 프론트가 이미 이 이름 기준으로
 * 개발 중이라 그대로 맞춘다 - 바꾸지 말 것. latitude/longitude는 지도 마커 표시를
 * 위해 추가됐고(REGION V6, RegionCoordinateSeedingService), 확정된 flat 구조를
 * 유지하기 위해 중첩 객체 대신 최상위 필드로 추가한다. 좌표 시딩 전에는 null일
 * 수 있다.
 *
 * percentileRank/attractivenessTier는 score_cache에 저장된 값이 아니라
 * ScoreCacheRepository.findRankingWithPercentile 조회 시점에 PERCENT_RANK()로
 * 계산된다(API 명세서 5.2.3절).
 */
public record RankingItem(
        String regionCode,
        String regionName,
        BigDecimal totalScore,
        BigDecimal populationScore,
        BigDecimal householdScore,
        BigDecimal densityScore,
        Double latitude,
        Double longitude,
        Double percentileRank,
        AttractivenessTier attractivenessTier
) {
}
