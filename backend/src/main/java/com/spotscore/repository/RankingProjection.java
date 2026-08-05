package com.spotscore.repository;

import java.math.BigDecimal;

/**
 * ScoreCacheRepository.findRankingWithPercentile 네이티브 쿼리의 결과 프로젝션.
 * percentileRank는 PERCENT_RANK() 윈도우 함수로 조회 시점에 계산되며(score_cache에
 * 저장하지 않음), getter 이름이 쿼리의 컬럼 별칭(AS ...)과 일치해야 한다.
 */
public interface RankingProjection {

    String getRegionCode();

    String getRegionName();

    BigDecimal getTotalScore();

    BigDecimal getPopulationScore();

    BigDecimal getHouseholdScore();

    BigDecimal getDensityScore();

    Double getLatitude();

    Double getLongitude();

    Double getPercentileRank();
}
