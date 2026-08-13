package com.spotscore.scoring;

import com.spotscore.domain.ScoreWeightConfig;
import com.spotscore.repository.ScoreWeightConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoreWeightServiceTest {

    // CLAUDE.md 가중치 산출 방법(v2)/연령 구성 지표(v3) 섹션의 확정 값 - V11 시드와 동일.
    private static final Map<String, Double> SEED = Map.of(
            "DEMAND_WEIGHT", 0.5,
            "SUPPLY_WEIGHT", 0.5,
            "POPULATION_RATIO", 0.75,
            "HOUSEHOLD_RATIO", 0.25,
            "CORE_WEIGHT", 0.75,
            "AGE_WEIGHT", 0.25
    );

    private ScoreWeightConfigRepository repository;
    private ScoreWeightService service;

    @BeforeEach
    void setUp() {
        repository = mock(ScoreWeightConfigRepository.class);
        for (Map.Entry<String, Double> entry : SEED.entrySet()) {
            when(repository.findByWeightKey(entry.getKey()))
                    .thenReturn(Optional.of(new ScoreWeightConfig(entry.getKey(), BigDecimal.valueOf(entry.getValue()))));
        }
        service = new ScoreWeightService(repository);
    }

    @Test
    void neutralGroupHasNoAgeWeightAndLeavesSumToOne() {
        LeafWeights weights = service.loadLeafWeights(WeightGroup.NEUTRAL);

        assertThat(weights.populationWeight()).isCloseTo(0.375, within(1e-9));
        assertThat(weights.householdWeight()).isCloseTo(0.125, within(1e-9));
        assertThat(weights.competitionWeight()).isCloseTo(0.5, within(1e-9));
        assertThat(weights.ageWeight()).isNull();
        assertThat(weights.hasAgeWeight()).isFalse();

        double sum = weights.populationWeight() + weights.householdWeight() + weights.competitionWeight();
        assertThat(sum).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void directionalGroupIncludesAgeWeightAndLeavesSumToOne() {
        LeafWeights weights = service.loadLeafWeights(WeightGroup.DIRECTIONAL);

        assertThat(weights.populationWeight()).isCloseTo(0.28125, within(1e-9));
        assertThat(weights.householdWeight()).isCloseTo(0.09375, within(1e-9));
        assertThat(weights.competitionWeight()).isCloseTo(0.375, within(1e-9));
        assertThat(weights.ageWeight()).isCloseTo(0.25, within(1e-9));
        assertThat(weights.hasAgeWeight()).isTrue();

        double sum = weights.populationWeight() + weights.householdWeight()
                + weights.competitionWeight() + weights.ageWeight();
        assertThat(sum).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void missingWeightKeyThrowsIllegalState() {
        ScoreWeightConfigRepository emptyRepository = mock(ScoreWeightConfigRepository.class);
        when(emptyRepository.findByWeightKey("DEMAND_WEIGHT")).thenReturn(Optional.empty());
        ScoreWeightService serviceWithMissingConfig = new ScoreWeightService(emptyRepository);

        assertThatThrownBy(() -> serviceWithMissingConfig.loadLeafWeights(WeightGroup.NEUTRAL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMAND_WEIGHT");
    }
}
