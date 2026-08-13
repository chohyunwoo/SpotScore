package com.spotscore.scoring;

import com.spotscore.domain.AgeDirection;
import com.spotscore.domain.AgeStat;
import com.spotscore.domain.IndustryAgeDirection;
import com.spotscore.domain.IndustryCategory;
import com.spotscore.domain.PopulationStat;
import com.spotscore.domain.Region;
import com.spotscore.domain.ScoreCache;
import com.spotscore.domain.StoreCount;
import com.spotscore.repository.AgeStatRepository;
import com.spotscore.repository.IndustryAgeDirectionRepository;
import com.spotscore.repository.PopulationStatRepository;
import com.spotscore.repository.ScoreCacheRepository;
import com.spotscore.repository.StoreCountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 정규화 + 하이브리드 AHP 가중치로 지역 x 업종 조합 점수를 계산해 SCORE_CACHE에
 * 저장한다. 정규화는 "이번 배치에서 수집된 지역 전체"를 모집단으로 삼아 상대
 * 비교하므로, 특정 배치 시점(year/snapshotDate)에 수집된 전체 데이터를 대상으로
 * 다시 계산한다.
 *
 * densityScore는 B-2(퍼센타일 랭크 + 최소 인구 기준)로 계산한다 - 인구 대비
 * 밀도를 그대로 min-max 정규화하던 B-1은 극단 이상치(예: 인구 19명 지역) 하나가
 * 전체 스케일을 왜곡하는 결함이 있어 교체됨(창업매력도_정의_재검토_기록.md 10절).
 *
 * ageScore(연령적합도)는 업종의 IndustryAgeDirection에 따라 NEUTRAL 업종은
 * 계산하지 않고(리프 3개, WeightGroup.NEUTRAL), POSITIVE/NEGATIVE 업종은 4번째
 * 리프로 totalScore에 포함한다(WeightGroup.DIRECTIONAL). 연령 비율의 퍼센타일은
 * 업종과 무관하게 지역 단위로 한 번만 계산한다(density처럼 업종별로 값이
 * 달라지지 않음) - AgeStatRepository.findAllByYear로 전체를 모아 density와 동일한
 * PercentileRankNormalizer로 일괄 계산한다(CLAUDE.md 연령 구성 지표 섹션).
 */
@Service
public class ScoreCalculationService {

    private static final Logger log = LoggerFactory.getLogger(ScoreCalculationService.class);

    // 통계적으로 도출된 값이 아니라 실무적 판단값 - 이 인구 미만인 지역은 해당
    // 업종에 대해 densityScore/totalScore를 계산하지 않고(다른 지역 순위 계산의
    // 모집단에서도 제외), 값을 임의로 만들지 않고 null로 응답한다. ageScore도
    // 동일 임계값·원칙을 재사용한다(CLAUDE.md 연령 구성 지표 섹션 1번).
    private static final int MIN_POPULATION_FOR_DENSITY = 100;

    private final PopulationStatRepository populationStatRepository;
    private final StoreCountRepository storeCountRepository;
    private final ScoreCacheRepository scoreCacheRepository;
    private final ScoreWeightService scoreWeightService;
    private final AgeStatRepository ageStatRepository;
    private final IndustryAgeDirectionRepository industryAgeDirectionRepository;

    public ScoreCalculationService(PopulationStatRepository populationStatRepository,
                                    StoreCountRepository storeCountRepository,
                                    ScoreCacheRepository scoreCacheRepository,
                                    ScoreWeightService scoreWeightService,
                                    AgeStatRepository ageStatRepository,
                                    IndustryAgeDirectionRepository industryAgeDirectionRepository) {
        this.populationStatRepository = populationStatRepository;
        this.storeCountRepository = storeCountRepository;
        this.scoreCacheRepository = scoreCacheRepository;
        this.scoreWeightService = scoreWeightService;
        this.ageStatRepository = ageStatRepository;
        this.industryAgeDirectionRepository = industryAgeDirectionRepository;
    }

    @Transactional
    public int recalculateAll(int year, LocalDate snapshotDate) {
        List<PopulationStat> stats = populationStatRepository.findAllByYear(year);
        if (stats.isEmpty()) {
            log.warn("점수 재계산 스킵 - year: {}에 대한 population_stat 데이터 없음", year);
            return 0;
        }

        List<StoreCount> storeCounts = storeCountRepository.findAllBySnapshotDate(snapshotDate);
        if (storeCounts.isEmpty()) {
            log.warn("점수 재계산 스킵 - snapshotDate: {}에 대한 store_count 데이터 없음", snapshotDate);
            return 0;
        }

        Map<Region, Double> rawPopulation = new LinkedHashMap<>();
        Map<Region, Double> rawHousehold = new LinkedHashMap<>();
        for (PopulationStat stat : stats) {
            Region region = stat.getRegion();
            if (stat.getTotalPopulation() != null) {
                rawPopulation.put(region, stat.getTotalPopulation().doubleValue());
            } else {
                log.warn("정규화 스킵 - regionCode: {}의 total_population 없음 (인구 규모 점수 계산 불가)",
                        region.getRegionCode());
            }
            if (stat.getTotalFamily() != null) {
                rawHousehold.put(region, stat.getTotalFamily().doubleValue());
            } else {
                log.warn("정규화 스킵 - regionCode: {}의 total_family 없음 (가구 구조 점수 계산 불가)",
                        region.getRegionCode());
            }
        }

        Map<Region, Double> populationNormalized = MinMaxNormalizer.normalize(rawPopulation, "population_scale");
        Map<Region, Double> householdNormalized = MinMaxNormalizer.normalize(rawHousehold, "household_structure");
        Map<Region, Double> agePercentile = calculateAgePercentile(year, rawPopulation);

        Map<IndustryCategory, Map<Region, Double>> rawCompetitionByIndustry = storeCounts.stream()
                .collect(Collectors.groupingBy(StoreCount::getIndustry,
                        Collectors.toMap(StoreCount::getRegion, sc -> (double) sc.getStoreCount())));

        LeafWeights neutralWeights = scoreWeightService.loadLeafWeights(WeightGroup.NEUTRAL);
        LeafWeights directionalWeights = scoreWeightService.loadLeafWeights(WeightGroup.DIRECTIONAL);

        int cacheSaved = 0;
        int densityExcludedLowPopulation = 0;
        int ageExcludedMissingData = 0;
        for (Map.Entry<IndustryCategory, Map<Region, Double>> entry : rawCompetitionByIndustry.entrySet()) {
            IndustryCategory industry = entry.getKey();
            Map<Region, Double> rawCounts = entry.getValue();

            AgeDirection ageDirection = resolveAgeDirection(industry);
            WeightGroup weightGroup = WeightGroup.from(ageDirection);
            LeafWeights weights = weightGroup == WeightGroup.NEUTRAL ? neutralWeights : directionalWeights;

            // 업소 개수 자체가 아니라 인구 대비 밀도(업소 수 / 총인구)로 경쟁 강도를 계산하되,
            // 인구 MIN_POPULATION_FOR_DENSITY 미만인 지역은 이 업종의 밀도 비교 모집단
            // 자체에서 완전히 제외한다 - 극단적으로 작은 인구가 만드는 이상치 값이 다른
            // 지역의 퍼센타일 순위에까지 영향을 주지 않도록 하기 위함(B-1의 결함 원인).
            Map<Region, Double> eligibleRawDensity = new LinkedHashMap<>();
            for (Map.Entry<Region, Double> countEntry : rawCounts.entrySet()) {
                Region region = countEntry.getKey();
                Double population = rawPopulation.get(region);
                if (population == null || population < MIN_POPULATION_FOR_DENSITY) {
                    log.warn("정규화 스킵 - regionCode: {}, industryCode: {} 인구 {}명 미만(또는 없음)이라 " +
                                    "densityScore 계산 대상에서 제외 (totalScore도 함께 null)",
                            region.getRegionCode(), industry.getIndustryCode(), MIN_POPULATION_FOR_DENSITY);
                    continue;
                }
                eligibleRawDensity.put(region, countEntry.getValue() / population);
            }

            // ScoreCacheRepository.findRankingWithPercentile / AttractivenessTier가 쓰는 것과
            // 동일한 PERCENT_RANK 정의를 재사용한다(PercentileRankNormalizer 참고).
            Map<Region, Double> densityPercentile = PercentileRankNormalizer.ascendingPercentRank(
                    eligibleRawDensity, "competition_density:" + industry.getIndustryCode());

            for (Region region : rawCounts.keySet()) {
                Double populationNorm = populationNormalized.get(region);
                Double householdNorm = householdNormalized.get(region);
                if (populationNorm == null || householdNorm == null) {
                    log.warn("점수 계산 스킵 - regionCode: {}, industryCode: {} 브레이크다운 원자료 일부 누락",
                            region.getRegionCode(), industry.getIndustryCode());
                    continue;
                }

                double populationScore = populationNorm * 100;
                double householdScore = householdNorm * 100;

                Double percentile = densityPercentile.get(region);
                if (percentile == null) {
                    // population < MIN_POPULATION_FOR_DENSITY - densityScore를 임의로
                    // 추정하지 않고, 그 값에 의존하는 totalScore도 함께 null로 둔다
                    // (populationStat null 처리와 동일한 컨벤션).
                    densityExcludedLowPopulation++;
                    saveScoreCache(region, industry, null, populationScore, householdScore, null, null);
                    cacheSaved++;
                    continue;
                }

                // 인구 대비 업소 밀도의 순위가 높을수록(=과열) 창업 매력도는 낮아지므로 퍼센타일을 역수화한다.
                double densityScore = (1 - percentile) * 100;

                Double ageScore = null;
                if (weightGroup == WeightGroup.DIRECTIONAL) {
                    Double ageRatioPercentile = agePercentile.get(region);
                    if (ageRatioPercentile == null) {
                        log.warn("ageScore 계산 스킵 - regionCode: {}, industryCode: {} 연령 원자료 없음/미달 " +
                                        "(또는 population {}명 미만) - totalScore도 함께 null 처리",
                                region.getRegionCode(), industry.getIndustryCode(), MIN_POPULATION_FOR_DENSITY);
                    } else {
                        ageScore = ageDirection == AgeDirection.POSITIVE
                                ? ageRatioPercentile * 100
                                : (1 - ageRatioPercentile) * 100;
                    }
                }

                Double totalScore;
                if (weightGroup == WeightGroup.NEUTRAL) {
                    totalScore = weights.populationWeight() * populationScore
                            + weights.householdWeight() * householdScore
                            + weights.competitionWeight() * densityScore;
                } else if (ageScore != null) {
                    totalScore = weights.populationWeight() * populationScore
                            + weights.householdWeight() * householdScore
                            + weights.competitionWeight() * densityScore
                            + weights.ageWeight() * ageScore;
                } else {
                    totalScore = null;
                    ageExcludedMissingData++;
                }

                log.debug("가중치 계산 결과 - regionCode: {}, industryCode: {}, weightGroup: {}, populationScore: {}, " +
                                "householdScore: {}, densityScore: {}, ageScore: {}, totalScore: {}",
                        region.getRegionCode(), industry.getIndustryCode(), weightGroup, populationScore,
                        householdScore, densityScore, ageScore, totalScore);

                saveScoreCache(region, industry, totalScore, populationScore, householdScore, densityScore, ageScore);
                cacheSaved++;
            }
        }

        log.info("점수 재계산 완료 - year: {}, snapshotDate: {}, score_cache 저장 {}건 (그 중 인구 {}명 미만으로 " +
                        "densityScore/totalScore null 처리 {}건, DIRECTIONAL인데 ageScore 원자료 없어 totalScore null " +
                        "처리 {}건)",
                year, snapshotDate, cacheSaved, MIN_POPULATION_FOR_DENSITY, densityExcludedLowPopulation,
                ageExcludedMissingData);
        return cacheSaved;
    }

    private AgeDirection resolveAgeDirection(IndustryCategory industry) {
        return industryAgeDirectionRepository.findById(industry.getIndustryCode())
                .map(IndustryAgeDirection::getDirection)
                .orElseGet(() -> {
                    log.warn("업종별 연령 방향성 매핑 없음 - industryCode: {} (IndustryAgeDirectionSeedingService 시딩 " +
                            "필요, 우선 NEUTRAL로 처리)", industry.getIndustryCode());
                    return AgeDirection.NEUTRAL;
                });
    }

    /**
     * 20~39세 비율의 퍼센타일 랭크를 지역 단위로 한 번만 계산한다(업종 무관 -
     * density와 달리 업종별로 값이 달라지지 않음). density와 동일하게
     * population(SGIS 기준) < MIN_POPULATION_FOR_DENSITY 지역은 모집단에서
     * 제외한다.
     */
    private Map<Region, Double> calculateAgePercentile(int year, Map<Region, Double> rawPopulation) {
        List<AgeStat> ageStats = ageStatRepository.findAllByYear(year);
        Map<Region, Double> eligibleAgeRatio = new LinkedHashMap<>();
        for (AgeStat ageStat : ageStats) {
            Region region = ageStat.getRegion();
            Double population = rawPopulation.get(region);
            if (population == null || population < MIN_POPULATION_FOR_DENSITY) {
                log.warn("정규화 스킵 - regionCode: {} 인구 {}명 미만(또는 없음)이라 ageScore 계산 대상에서 제외",
                        region.getRegionCode(), MIN_POPULATION_FOR_DENSITY);
                continue;
            }
            if (ageStat.getAge2039Cnt() == null || ageStat.getKosisTotalPopulation() == null
                    || ageStat.getKosisTotalPopulation() <= 0) {
                log.warn("정규화 스킵 - regionCode: {}, year: {} age_stat 원자료 없음/미수집이라 ageScore 계산 대상에서 제외",
                        region.getRegionCode(), year);
                continue;
            }
            eligibleAgeRatio.put(region, ageStat.getAge2039Cnt() / (double) ageStat.getKosisTotalPopulation());
        }
        return PercentileRankNormalizer.ascendingPercentRank(eligibleAgeRatio, "age_ratio:" + year);
    }

    private void saveScoreCache(Region region, IndustryCategory industry, Double totalScore,
                                 double populationScore, double householdScore, Double densityScore,
                                 Double ageScore) {
        LocalDateTime calculatedAt = LocalDateTime.now();
        BigDecimal totalScoreRounded = round(totalScore);
        BigDecimal populationScoreRounded = round(populationScore);
        BigDecimal householdScoreRounded = round(householdScore);
        BigDecimal densityScoreRounded = round(densityScore);
        BigDecimal ageScoreRounded = round(ageScore);

        scoreCacheRepository.findByRegionAndIndustry(region, industry)
                .ifPresentOrElse(
                        existing -> existing.update(totalScoreRounded, populationScoreRounded, householdScoreRounded,
                                densityScoreRounded, ageScoreRounded, calculatedAt),
                        () -> scoreCacheRepository.save(new ScoreCache(region, industry, totalScoreRounded,
                                populationScoreRounded, householdScoreRounded, densityScoreRounded, ageScoreRounded,
                                calculatedAt))
                );
    }

    private static BigDecimal round(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
