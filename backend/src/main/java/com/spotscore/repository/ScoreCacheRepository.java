package com.spotscore.repository;

import com.spotscore.domain.IndustryCategory;
import com.spotscore.domain.Region;
import com.spotscore.domain.ScoreCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScoreCacheRepository extends JpaRepository<ScoreCache, Long> {

    Optional<ScoreCache> findByRegionAndIndustry(Region region, IndustryCategory industry);

    // percentileRank는 같은 industryCode 파티션 안에서 totalScore 내림차순 기준
    // PERCENT_RANK()로 조회 시점에 계산한다(score_cache에는 저장하지 않음 - API
    // 명세서 5.2.3절). 윈도우 함수는 JPQL이 지원하지 않아 네이티브 쿼리로 두고,
    // 프로젝션(RankingProjection)의 getter 이름을 컬럼 별칭과 맞춘다.
    @Query(value = "SELECT sc.region_code AS regionCode, r.region_name AS regionName, " +
            "sc.total_score AS totalScore, sc.population_score AS populationScore, " +
            "sc.household_score AS householdScore, sc.density_score AS densityScore, " +
            "r.latitude AS latitude, r.longitude AS longitude, " +
            "PERCENT_RANK() OVER (PARTITION BY sc.industry_code ORDER BY sc.total_score DESC) * 100 AS percentileRank " +
            "FROM score_cache sc JOIN region r ON r.region_code = sc.region_code " +
            "WHERE sc.industry_code = :industryCode " +
            "ORDER BY sc.total_score DESC",
            nativeQuery = true)
    List<RankingProjection> findRankingWithPercentile(@Param("industryCode") String industryCode);

    @Query("SELECT sc FROM ScoreCache sc JOIN FETCH sc.region JOIN FETCH sc.industry " +
            "WHERE sc.region.regionCode = :regionCode AND sc.industry.industryCode = :industryCode")
    Optional<ScoreCache> findByRegion_RegionCodeAndIndustry_IndustryCode(
            @Param("regionCode") String regionCode, @Param("industryCode") String industryCode);

    // region_code 교정 시 옛(틀린) 코드 밑에 남은 score_cache를 정리한다 - 방어적
    // 처리(RegionCrosswalkRebuildService 참고, StoreCountRepository와 동일한 이유).
    void deleteByRegion_RegionCode(String regionCode);
}
