package com.spotscore.scoring;

import com.spotscore.domain.AgeDirection;
import com.spotscore.domain.AgeStat;
import com.spotscore.domain.IndustryAgeDirection;
import com.spotscore.domain.IndustryCategory;
import com.spotscore.domain.PopulationStat;
import com.spotscore.domain.Region;
import com.spotscore.domain.ScoreCache;
import com.spotscore.domain.ScoreWeightConfig;
import com.spotscore.domain.StoreCount;
import com.spotscore.repository.AgeStatRepository;
import com.spotscore.repository.IndustryAgeDirectionRepository;
import com.spotscore.repository.PopulationStatRepository;
import com.spotscore.repository.ScoreCacheRepository;
import com.spotscore.repository.ScoreWeightConfigRepository;
import com.spotscore.repository.StoreCountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NEUTRAL 업종은 회귀 없이 3리프 그대로 계산되고, DIRECTIONAL 업종(POSITIVE/NEGATIVE)은
 * ageScore가 4번째 리프로 반영되며 방향에 따라 퍼센타일이 반전되는지 검증한다
 * (이슈: 연령적합도를 실제 종합 점수 계산에 반영).
 *
 * 지역 2개(R1/R2)로 구성해 PercentileRankNormalizer의 n=2 케이스(작은 값 -> 0,
 * 큰 값 -> 1)를 그대로 손계산 가능하게 했다 - population/household는 min-max로
 * (0, 100), density/age는 percent-rank로 (0, 100)이 나오도록 원자료를 설계했다.
 */
class ScoreCalculationServiceTest {

    private static final int YEAR = 2026;
    private static final LocalDate SNAPSHOT_DATE = LocalDate.parse("2026-08-13");

    // CLAUDE.md 가중치 산출 방법(v2)/연령 구성 지표(v3) 확정 값 - V11 시드와 동일.
    private static final Map<String, Double> WEIGHT_SEED = Map.of(
            "DEMAND_WEIGHT", 0.5,
            "SUPPLY_WEIGHT", 0.5,
            "POPULATION_RATIO", 0.75,
            "HOUSEHOLD_RATIO", 0.25,
            "CORE_WEIGHT", 0.75,
            "AGE_WEIGHT", 0.25
    );

    private final Region regionLowYouth = new Region("11111111", "저청년동", "DONG", null);
    private final Region regionHighYouth = new Region("22222222", "고청년동", "DONG", null);
    private final IndustryCategory neutralIndustry = new IndustryCategory("N1", "중립업종", "MAJOR");
    private final IndustryCategory positiveIndustry = new IndustryCategory("POS1", "청년선호업종", "MAJOR");
    private final IndustryCategory negativeIndustry = new IndustryCategory("NEG1", "고령선호업종", "MAJOR");

    private PopulationStatRepository populationStatRepository;
    private StoreCountRepository storeCountRepository;
    private ScoreCacheRepository scoreCacheRepository;
    private AgeStatRepository ageStatRepository;
    private IndustryAgeDirectionRepository industryAgeDirectionRepository;
    private ScoreCalculationService service;

    @BeforeEach
    void setUp() {
        populationStatRepository = mock(PopulationStatRepository.class);
        storeCountRepository = mock(StoreCountRepository.class);
        scoreCacheRepository = mock(ScoreCacheRepository.class);
        ageStatRepository = mock(AgeStatRepository.class);
        industryAgeDirectionRepository = mock(IndustryAgeDirectionRepository.class);

        ScoreWeightConfigRepository weightConfigRepository = mock(ScoreWeightConfigRepository.class);
        for (Map.Entry<String, Double> entry : WEIGHT_SEED.entrySet()) {
            when(weightConfigRepository.findByWeightKey(entry.getKey()))
                    .thenReturn(Optional.of(new ScoreWeightConfig(entry.getKey(), BigDecimal.valueOf(entry.getValue()))));
        }
        ScoreWeightService scoreWeightService = new ScoreWeightService(weightConfigRepository);

        // 인구: R1=1000, R2=2000 -> min-max (0, 100). 가구: R1=400, R2=800 -> 동일 비율 (0, 100).
        when(populationStatRepository.findAllByYear(YEAR)).thenReturn(List.of(
                new PopulationStat(regionLowYouth, YEAR, 1000L, null, 400L, null),
                new PopulationStat(regionHighYouth, YEAR, 2000L, null, 800L, null)
        ));

        // 업종 3개 모두 storeCount 동일(R1=1, R2=1)하게 둬서, 세 업종의 population/household/
        // density 리프는 완전히 동일하고 age 리프만 방향에 따라 달라지게 만든다.
        when(storeCountRepository.findAllBySnapshotDate(SNAPSHOT_DATE)).thenReturn(List.of(
                new StoreCount(regionLowYouth, neutralIndustry, 1, SNAPSHOT_DATE),
                new StoreCount(regionHighYouth, neutralIndustry, 1, SNAPSHOT_DATE),
                new StoreCount(regionLowYouth, positiveIndustry, 1, SNAPSHOT_DATE),
                new StoreCount(regionHighYouth, positiveIndustry, 1, SNAPSHOT_DATE),
                new StoreCount(regionLowYouth, negativeIndustry, 1, SNAPSHOT_DATE),
                new StoreCount(regionHighYouth, negativeIndustry, 1, SNAPSHOT_DATE)
        ));

        // 20~39세 비율: R1 20/100=20%(저청년), R2 80/100=80%(고청년).
        when(ageStatRepository.findAllByYear(YEAR)).thenReturn(List.of(
                new AgeStat(regionLowYouth, YEAR, 20L, 100L, SNAPSHOT_DATE),
                new AgeStat(regionHighYouth, YEAR, 80L, 100L, SNAPSHOT_DATE)
        ));

        when(industryAgeDirectionRepository.findById("N1")).thenReturn(Optional.empty());
        when(industryAgeDirectionRepository.findById("POS1"))
                .thenReturn(Optional.of(new IndustryAgeDirection("POS1", AgeDirection.POSITIVE)));
        when(industryAgeDirectionRepository.findById("NEG1"))
                .thenReturn(Optional.of(new IndustryAgeDirection("NEG1", AgeDirection.NEGATIVE)));

        when(scoreCacheRepository.findByRegionAndIndustry(any(), any())).thenReturn(Optional.empty());

        service = new ScoreCalculationService(populationStatRepository, storeCountRepository, scoreCacheRepository,
                scoreWeightService, ageStatRepository, industryAgeDirectionRepository);
    }

    @Test
    void neutralIndustryIgnoresAgeScoreAndKeepsThreeLeafCalculation() {
        // N1은 industry_age_direction에 매핑이 아예 없는 케이스(setUp의 Optional.empty)이기도 해서,
        // AgeScoreService와 동일한 NEUTRAL 폴백 동작까지 함께 검증한다.
        service.recalculateAll(YEAR, SNAPSHOT_DATE);

        ScoreCache lowYouth = findSaved("N1", "11111111");
        ScoreCache highYouth = findSaved("N1", "22222222");

        assertThat(lowYouth.getAgeScore()).isNull();
        assertThat(highYouth.getAgeScore()).isNull();
        // NEUTRAL 리프: population=0.375, household=0.125, density=0.5 (V11 시드)
        assertThat(lowYouth.getTotalScore().doubleValue()).isCloseTo(0.0, within(0.01));
        assertThat(highYouth.getTotalScore().doubleValue()).isCloseTo(100.0, within(0.01));
    }

    @Test
    void positiveDirectionalIndustryScoresHighYouthRegionHigher() {
        service.recalculateAll(YEAR, SNAPSHOT_DATE);

        ScoreCache lowYouth = findSaved("POS1", "11111111");
        ScoreCache highYouth = findSaved("POS1", "22222222");

        // POSITIVE: 퍼센타일 그대로 -> 고청년(R2)이 ageScore 100, 저청년(R1)이 0
        assertThat(lowYouth.getAgeScore().doubleValue()).isCloseTo(0.0, within(0.01));
        assertThat(highYouth.getAgeScore().doubleValue()).isCloseTo(100.0, within(0.01));
        assertThat(lowYouth.getTotalScore().doubleValue()).isCloseTo(0.0, within(0.01));
        assertThat(highYouth.getTotalScore().doubleValue()).isCloseTo(100.0, within(0.01));
    }

    @Test
    void negativeDirectionalIndustryReversesAgePercentileLikeQ1() {
        service.recalculateAll(YEAR, SNAPSHOT_DATE);

        ScoreCache lowYouth = findSaved("NEG1", "11111111");
        ScoreCache highYouth = findSaved("NEG1", "22222222");

        // NEGATIVE(Q1과 동일 방향): 퍼센타일 반전 -> 저청년(고령층 많은 R1)이 ageScore 100,
        // 고청년(R2)이 0 - POSITIVE와 정반대로 나와야 한다.
        assertThat(lowYouth.getAgeScore().doubleValue()).isCloseTo(100.0, within(0.01));
        assertThat(highYouth.getAgeScore().doubleValue()).isCloseTo(0.0, within(0.01));

        // population/household/density 리프는 POS1과 완전히 동일한데(같은 storeCount/인구),
        // age 리프만 반전되어 totalScore가 달라진다: DIRECTIONAL 가중치(0.28125/0.09375/
        // 0.375/0.25) 기준 R1은 0 -> 25(age 25점 반영분), R2는 100 -> 75(age 0점 반영분).
        assertThat(lowYouth.getTotalScore().doubleValue()).isCloseTo(25.0, within(0.01));
        assertThat(highYouth.getTotalScore().doubleValue()).isCloseTo(75.0, within(0.01));
    }

    private ScoreCache findSaved(String industryCode, String regionCode) {
        ArgumentCaptor<ScoreCache> captor = ArgumentCaptor.forClass(ScoreCache.class);
        verify(scoreCacheRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(sc -> sc.getIndustry().getIndustryCode().equals(industryCode)
                        && sc.getRegion().getRegionCode().equals(regionCode))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError(
                        "저장된 ScoreCache 없음 - industryCode: " + industryCode + ", regionCode: " + regionCode));
    }
}
