package com.spotscore.scoring;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * SQL {@code PERCENT_RANK() OVER (ORDER BY value ASC)}와 동일한 정의
 * (percentile = 자신보다 작은 값의 개수 / (N-1))를 자바 구현이 그대로 지키는지
 * 검증한다. min-max와 달리 극단 이상치의 "값 크기"가 아니라 "순위"만 반영하는 게
 * 핵심이라(둔촌1동 이상치 사건, CLAUDE.md), 이상치가 있어도 순위 기반으로만
 * 계산되는지 함께 확인한다.
 */
class PercentileRankNormalizerTest {

    @Test
    void emptyInputReturnsEmptyMap() {
        assertThat(PercentileRankNormalizer.ascendingPercentRank(Map.of(), "density")).isEmpty();
    }

    @Test
    void singleValueIsZeroByDefinition() {
        // n=1이면 분모(N-1)가 0이라 나눗셈이 불가 - PERCENT_RANK 정의상 0으로 처리한다.
        Map<String, Double> result = PercentileRankNormalizer.ascendingPercentRank(Map.of("only", 999.0), "density");

        assertThat(result).containsOnlyKeys("only");
        assertThat(result.get("only")).isEqualTo(0.0);
    }

    @Test
    void twoValuesMapToZeroAndOne() {
        Map<String, Double> raw = new LinkedHashMap<>();
        raw.put("small", 5.0);
        raw.put("large", 50.0);

        Map<String, Double> result = PercentileRankNormalizer.ascendingPercentRank(raw, "density");

        assertThat(result.get("small")).isCloseTo(0.0, within(1e-9));
        assertThat(result.get("large")).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void rankIsBasedOnOrderNotMagnitudeSoOutlierDoesNotDominate() {
        // 값 [10, 20, 21, 1000]. min-max라면 1000이 스케일을 지배하지만, 퍼센타일은
        // 순위만 반영하므로 균등하게 0, 1/3, 2/3, 1로 나온다.
        Map<String, Double> raw = new LinkedHashMap<>();
        raw.put("a", 10.0);
        raw.put("b", 20.0);
        raw.put("c", 21.0);
        raw.put("outlier", 1000.0);

        Map<String, Double> result = PercentileRankNormalizer.ascendingPercentRank(raw, "density");

        assertThat(result.get("a")).isCloseTo(0.0, within(1e-9));
        assertThat(result.get("b")).isCloseTo(1.0 / 3.0, within(1e-9));
        assertThat(result.get("c")).isCloseTo(2.0 / 3.0, within(1e-9));
        assertThat(result.get("outlier")).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void tiedValuesShareTheSameLowerRank() {
        // 값 [10, 20, 20, 30], N=4, 분모=3. 동점(20)은 둘 다 "자신보다 작은 값 1개" -> 1/3.
        Map<String, Double> raw = new LinkedHashMap<>();
        raw.put("a", 10.0);
        raw.put("b", 20.0);
        raw.put("c", 20.0);
        raw.put("d", 30.0);

        Map<String, Double> result = PercentileRankNormalizer.ascendingPercentRank(raw, "density");

        assertThat(result.get("a")).isCloseTo(0.0, within(1e-9));
        assertThat(result.get("b")).isCloseTo(1.0 / 3.0, within(1e-9));
        assertThat(result.get("c")).isCloseTo(1.0 / 3.0, within(1e-9));
        assertThat(result.get("d")).isCloseTo(1.0, within(1e-9));
    }
}
