package com.spotscore.dto;

import com.spotscore.domain.IndustryCategory;
import com.spotscore.domain.PopulationStat;
import com.spotscore.domain.Region;
import com.spotscore.domain.ScoreCache;
import com.spotscore.domain.StoreCount;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * GET /api/v1/scores/detail 응답. 종합/브레이크다운 점수와 함께, 상세 패널이
 * "왜 이 점수인가"를 보여줄 수 있도록 각 브레이크다운의 원자료 값을 populationStat/
 * competitionStat에 함께 담는다 (CLAUDE.md 대시보드 화면 구성).
 *
 * populationStat의 householdCount/avgHouseholdSize는 DB 컬럼명(total_family,
 * avg_family_member_count)과 다르게 프론트가 이미 가정한 이름으로 매핑해서 내려준다
 * - 바꾸지 말 것.
 *
 * householdCount/avgHouseholdSize가 null일 때 "0 처리"할지 "데이터 없음"으로
 * 표시할지는 CLAUDE.md에 아직 결정되지 않은 사항으로 남아있다 - 백엔드가 임의로
 * 정하지 않고, 값은 null 그대로 두고 dataAvailable로 "원래 없는 값인지"를 그대로
 * 알려준다. 이 결정은 프론트에 위임한다.
 */
public record ScoreDetailResponse(
        String regionCode,
        String regionName,
        String industryCode,
        String industryName,
        BigDecimal totalScore,
        BigDecimal populationScore,
        BigDecimal householdScore,
        BigDecimal densityScore,
        PopulationStatDetail populationStat,
        CompetitionStatDetail competitionStat,
        LocalDateTime calculatedAt
) {

    public record PopulationStatDetail(
            Integer year,
            Long totalPopulation,
            BigDecimal density,
            Long householdCount,
            Double avgHouseholdSize,
            boolean dataAvailable
    ) {

        static PopulationStatDetail from(PopulationStat entity) {
            if (entity == null) {
                return null;
            }
            boolean dataAvailable = entity.getTotalFamily() != null && entity.getAvgFamilyMemberCount() != null;
            return new PopulationStatDetail(
                    entity.getYear(),
                    entity.getTotalPopulation(),
                    entity.getDensity(),
                    entity.getTotalFamily(),
                    entity.getAvgFamilyMemberCount(),
                    dataAvailable
            );
        }
    }

    public record CompetitionStatDetail(
            Integer storeCount,
            LocalDate snapshotDate
    ) {

        static CompetitionStatDetail from(StoreCount entity) {
            if (entity == null) {
                return null;
            }
            return new CompetitionStatDetail(entity.getStoreCount(), entity.getSnapshotDate());
        }
    }

    public static ScoreDetailResponse of(ScoreCache scoreCache, PopulationStat populationStat, StoreCount storeCount) {
        Region region = scoreCache.getRegion();
        IndustryCategory industry = scoreCache.getIndustry();
        return new ScoreDetailResponse(
                region.getRegionCode(),
                region.getRegionName(),
                industry.getIndustryCode(),
                industry.getIndustryName(),
                scoreCache.getTotalScore(),
                scoreCache.getPopulationScore(),
                scoreCache.getHouseholdScore(),
                scoreCache.getDensityScore(),
                PopulationStatDetail.from(populationStat),
                CompetitionStatDetail.from(storeCount),
                scoreCache.getCalculatedAt()
        );
    }
}
