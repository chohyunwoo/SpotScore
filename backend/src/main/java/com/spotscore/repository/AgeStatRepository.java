package com.spotscore.repository;

import com.spotscore.domain.AgeStat;
import com.spotscore.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgeStatRepository extends JpaRepository<AgeStat, Long> {

    Optional<AgeStat> findByRegionAndYear(Region region, int year);

    // ScoreCalculationService 배치용 - 연령 퍼센타일은 업종과 무관하게 지역
    // 단위로 한 번만 계산하면 되므로(density처럼 업종별로 값이 달라지지 않음),
    // findAgeRatioPercentileRank(지역 1개씩 조회하는 상세 API 전용 쿼리)를 배치
    // 루프에서 재사용하면 N+1이 된다 - 대신 이 목록을 인메모리로 모아
    // PercentileRankNormalizer로 일괄 계산한다.
    List<AgeStat> findAllByYear(int year);

    // ageRatioPercent(20~39세 비중)의 PERCENT_RANK()를 조회 시점에 계산한다 - CLAUDE.md
    // "점수 해석 기준"과 동일한 패턴(ScoreCacheRepository.findRankingWithPercentile)을
    // 재사용하되, densityScore와 동일한 최소 인구 기준(population_stat.total_population >=
    // :minPopulation, ScoreCalculationService.MIN_POPULATION_FOR_DENSITY와 동일 임계값·원칙)
    // 으로 모집단을 제한한다. year는 조회 대상 population_stat/age_stat과 동일한 연도로 맞춘다.
    @Query(value = "SELECT ranked.percentile_rank AS percentileRank FROM (" +
            "  SELECT a.region_code AS region_code, " +
            "         PERCENT_RANK() OVER (ORDER BY (a.age2039_cnt::numeric / a.kosis_total_population) ASC) AS percentile_rank " +
            "  FROM age_stat a " +
            "  JOIN population_stat p ON p.region_code = a.region_code AND p.year = a.year " +
            "  WHERE a.year = :year AND a.age2039_cnt IS NOT NULL AND a.kosis_total_population > 0 " +
            "        AND p.total_population >= :minPopulation" +
            ") ranked WHERE ranked.region_code = :regionCode",
            nativeQuery = true)
    List<AgePercentileProjection> findAgeRatioPercentileRank(@Param("regionCode") String regionCode,
                                                              @Param("year") int year,
                                                              @Param("minPopulation") int minPopulation);

    // region_code 교정 시 옛 코드 밑에 남은 age_stat을 정리한다 - 다른 원자료
    // repository(PopulationStatRepository 등)와 동일한 방어적 처리
    // (RegionCrosswalkRebuildService 참고).
    void deleteByRegion_RegionCode(String regionCode);
}
