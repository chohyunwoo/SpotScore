package com.spotscore.scoring;

import com.spotscore.domain.AgeDirection;
import com.spotscore.domain.AgeStat;
import com.spotscore.domain.IndustryAgeDirection;
import com.spotscore.domain.Region;
import com.spotscore.dto.ScoreDetailResponse.AgeStatDetail;
import com.spotscore.repository.AgePercentileProjection;
import com.spotscore.repository.AgeStatRepository;
import com.spotscore.repository.IndustryAgeDirectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * ageScore(연령 구성 지표) 계산. 아직 totalScore/v2 가중치에는 반영하지 않고
 * (CLAUDE.md 연령 구성 지표 섹션 - 3단계는 응답 노출만), GET /api/v1/scores/detail
 * 응답에 ageStat으로만 노출한다. 기존 populationScore/householdScore/densityScore
 * 계산 로직(ScoreCalculationService)과 v2 가중치는 이 클래스와 무관하게 그대로 둔다.
 */
@Service
public class AgeScoreService {

    private static final Logger log = LoggerFactory.getLogger(AgeScoreService.class);

    // ScoreCalculationService.MIN_POPULATION_FOR_DENSITY와 동일한 임계값·원칙(둔촌1동 등
    // 극단 이상치 배제) - 기존 스코어링 로직은 이번 작업으로 변경하지 않는다는 제약이 있어
    // 상수를 그 클래스에서 가져오지 않고 이 클래스에 별도로 둔다.
    private static final int MIN_POPULATION_FOR_AGE_SCORE = 100;
    private static final String DATA_SOURCE = "KOSIS(주민등록인구 기준)";

    private final AgeStatRepository ageStatRepository;
    private final IndustryAgeDirectionRepository industryAgeDirectionRepository;

    public AgeScoreService(AgeStatRepository ageStatRepository,
                            IndustryAgeDirectionRepository industryAgeDirectionRepository) {
        this.ageStatRepository = ageStatRepository;
        this.industryAgeDirectionRepository = industryAgeDirectionRepository;
    }

    public AgeStatDetail computeAgeStat(Region region, String industryCode, Long sgisPopulation, Integer year) {
        AgeDirection direction = industryAgeDirectionRepository.findById(industryCode)
                .map(IndustryAgeDirection::getDirection)
                .orElseGet(() -> {
                    log.warn("업종별 연령 방향성 매핑 없음 - industryCode: {} (IndustryAgeDirectionSeedingService 시딩 필요, " +
                            "우선 NEUTRAL로 처리)", industryCode);
                    return AgeDirection.NEUTRAL;
                });

        if (sgisPopulation == null || sgisPopulation < MIN_POPULATION_FOR_AGE_SCORE) {
            log.warn("ageScore 계산 스킵 - regionCode: {} 인구(SGIS 기준) {}명 미만(또는 없음)",
                    region.getRegionCode(), MIN_POPULATION_FOR_AGE_SCORE);
            return new AgeStatDetail(null, null, direction, DATA_SOURCE);
        }
        if (year == null) {
            log.warn("ageScore 계산 스킵 - regionCode: {} 기준 연도(population_stat) 없음", region.getRegionCode());
            return new AgeStatDetail(null, null, direction, DATA_SOURCE);
        }

        AgeStat ageStat = ageStatRepository.findByRegionAndYear(region, year).orElse(null);
        if (ageStat == null || ageStat.getAge2039Cnt() == null
                || ageStat.getKosisTotalPopulation() == null || ageStat.getKosisTotalPopulation() <= 0) {
            log.warn("ageScore 계산 스킵 - regionCode: {}, year: {} age_stat 원자료 없음/미수집", region.getRegionCode(), year);
            return new AgeStatDetail(null, null, direction, DATA_SOURCE);
        }

        BigDecimal ageRatioPercent = BigDecimal.valueOf(ageStat.getAge2039Cnt())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(ageStat.getKosisTotalPopulation()), 1, RoundingMode.HALF_UP);

        if (direction == AgeDirection.NEUTRAL) {
            // NEUTRAL 업종은 원자료(ageRatioPercent)는 보여주되 ageScore는 계산하지 않는다
            // (가중치 반영 대상에서 제외 - CLAUDE.md 연령 구성 지표 섹션 2단계).
            return new AgeStatDetail(ageRatioPercent, null, direction, DATA_SOURCE);
        }

        List<AgePercentileProjection> ranked = ageStatRepository.findAgeRatioPercentileRank(
                region.getRegionCode(), year, MIN_POPULATION_FOR_AGE_SCORE);
        if (ranked.isEmpty() || ranked.get(0).getPercentileRank() == null) {
            log.warn("ageScore 계산 스킵 - regionCode: {}, year: {} 퍼센타일 계산 모집단에서 제외됨(인구 기준 미달 또는 데이터 없음)",
                    region.getRegionCode(), year);
            return new AgeStatDetail(ageRatioPercent, null, direction, DATA_SOURCE);
        }

        double percentile = ranked.get(0).getPercentileRank();
        double rawAgeScore = direction == AgeDirection.POSITIVE ? percentile * 100 : (1 - percentile) * 100;
        BigDecimal ageScore = BigDecimal.valueOf(rawAgeScore).setScale(2, RoundingMode.HALF_UP);

        return new AgeStatDetail(ageRatioPercent, ageScore, direction, DATA_SOURCE);
    }
}
