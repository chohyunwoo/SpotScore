package com.spotscore.scoring;

import com.spotscore.domain.IndustryCategory;
import com.spotscore.domain.PopulationStat;
import com.spotscore.domain.Region;
import com.spotscore.domain.ScoreCache;
import com.spotscore.domain.StoreCount;
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
 * 정규화(min-max) + 하이브리드 AHP 가중치로 지역 x 업종 조합 점수를 계산해
 * SCORE_CACHE에 저장한다. 정규화는 "이번 배치에서 수집된 지역 전체"를 모집단으로
 * 삼아 상대 비교하므로, 특정 배치 시점(year/snapshotDate)에 수집된 전체 데이터를
 * 대상으로 다시 계산한다.
 */
@Service
public class ScoreCalculationService {

    private static final Logger log = LoggerFactory.getLogger(ScoreCalculationService.class);

    private final PopulationStatRepository populationStatRepository;
    private final StoreCountRepository storeCountRepository;
    private final ScoreCacheRepository scoreCacheRepository;
    private final ScoreWeightService scoreWeightService;

    public ScoreCalculationService(PopulationStatRepository populationStatRepository,
                                    StoreCountRepository storeCountRepository,
                                    ScoreCacheRepository scoreCacheRepository,
                                    ScoreWeightService scoreWeightService) {
        this.populationStatRepository = populationStatRepository;
        this.storeCountRepository = storeCountRepository;
        this.scoreCacheRepository = scoreCacheRepository;
        this.scoreWeightService = scoreWeightService;
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

        Map<IndustryCategory, Map<Region, Double>> rawCompetitionByIndustry = storeCounts.stream()
                .collect(Collectors.groupingBy(StoreCount::getIndustry,
                        Collectors.toMap(StoreCount::getRegion, sc -> (double) sc.getStoreCount())));

        LeafWeights weights = scoreWeightService.loadLeafWeights();

        int cacheSaved = 0;
        for (Map.Entry<IndustryCategory, Map<Region, Double>> entry : rawCompetitionByIndustry.entrySet()) {
            IndustryCategory industry = entry.getKey();
            Map<Region, Double> rawCounts = entry.getValue();
            Map<Region, Double> competitionNormalized =
                    MinMaxNormalizer.normalize(rawCounts, "competition_density:" + industry.getIndustryCode());

            for (Region region : rawCounts.keySet()) {
                Double populationNorm = populationNormalized.get(region);
                Double householdNorm = householdNormalized.get(region);
                Double competitionNorm = competitionNormalized.get(region);
                if (populationNorm == null || householdNorm == null || competitionNorm == null) {
                    log.warn("점수 계산 스킵 - regionCode: {}, industryCode: {} 브레이크다운 원자료 일부 누락",
                            region.getRegionCode(), industry.getIndustryCode());
                    continue;
                }

                // 업소 수가 많을수록 경쟁이 치열해 창업 매력도는 낮아지므로 정규화 값을 역수화한다.
                double populationScore = populationNorm * 100;
                double householdScore = householdNorm * 100;
                double densityScore = (1 - competitionNorm) * 100;
                double totalScore = weights.populationWeight() * populationScore
                        + weights.householdWeight() * householdScore
                        + weights.competitionWeight() * densityScore;

                log.debug("가중치 계산 결과 - regionCode: {}, industryCode: {}, populationScore: {}, householdScore: {}, " +
                                "densityScore: {}, totalScore: {}",
                        region.getRegionCode(), industry.getIndustryCode(), populationScore, householdScore,
                        densityScore, totalScore);

                saveScoreCache(region, industry, totalScore, populationScore, householdScore, densityScore);
                cacheSaved++;
            }
        }

        log.info("점수 재계산 완료 - year: {}, snapshotDate: {}, score_cache 저장 {}건", year, snapshotDate, cacheSaved);
        return cacheSaved;
    }

    private void saveScoreCache(Region region, IndustryCategory industry, double totalScore,
                                 double populationScore, double householdScore, double densityScore) {
        LocalDateTime calculatedAt = LocalDateTime.now();
        BigDecimal totalScoreRounded = round(totalScore);
        BigDecimal populationScoreRounded = round(populationScore);
        BigDecimal householdScoreRounded = round(householdScore);
        BigDecimal densityScoreRounded = round(densityScore);

        scoreCacheRepository.findByRegionAndIndustry(region, industry)
                .ifPresentOrElse(
                        existing -> existing.update(totalScoreRounded, populationScoreRounded, householdScoreRounded,
                                densityScoreRounded, calculatedAt),
                        () -> scoreCacheRepository.save(new ScoreCache(region, industry, totalScoreRounded,
                                populationScoreRounded, householdScoreRounded, densityScoreRounded, calculatedAt))
                );
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
