package com.spotscore.scoring;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * min-max 정규화의 경계 동작을 검증한다. 핵심은 "분모(max-min)가 0인 경우
 * 예외 대신 중립값(0.5)으로 대체"하는 이상치 처리 - 비교 대상이 1건뿐이거나
 * 값이 모두 같은 배치에서 점수 계산이 죽지 않아야 한다(CLAUDE.md 로깅 가이드).
 */
class MinMaxNormalizerTest {

    @Test
    void emptyInputReturnsEmptyMap() {
        assertThat(MinMaxNormalizer.normalize(Map.of(), "population")).isEmpty();
    }

    @Test
    void normalizesToZeroAndOneAtEnds() {
        Map<String, Double> raw = new LinkedHashMap<>();
        raw.put("min", 100.0);
        raw.put("mid", 200.0);
        raw.put("max", 300.0);

        Map<String, Double> result = MinMaxNormalizer.normalize(raw, "population");

        assertThat(result.get("min")).isCloseTo(0.0, within(1e-9));
        assertThat(result.get("mid")).isCloseTo(0.5, within(1e-9));
        assertThat(result.get("max")).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void singleValueFallsBackToNeutralScore() {
        // 비교 대상 1건 -> range 0 -> 중립값 0.5 (예외를 던지지 않아야 함)
        Map<String, Double> result = MinMaxNormalizer.normalize(Map.of("only", 42.0), "population");

        assertThat(result).containsOnlyKeys("only");
        assertThat(result.get("only")).isEqualTo(0.5);
    }

    @Test
    void allEqualValuesFallBackToNeutralScore() {
        Map<String, Double> raw = new LinkedHashMap<>();
        raw.put("a", 7.0);
        raw.put("b", 7.0);
        raw.put("c", 7.0);

        Map<String, Double> result = MinMaxNormalizer.normalize(raw, "household");

        assertThat(result.values()).containsExactly(0.5, 0.5, 0.5);
    }

    @Test
    void handlesNegativeValues() {
        Map<String, Double> raw = new LinkedHashMap<>();
        raw.put("low", -10.0);
        raw.put("high", 10.0);

        Map<String, Double> result = MinMaxNormalizer.normalize(raw, "density");

        assertThat(result.get("low")).isCloseTo(0.0, within(1e-9));
        assertThat(result.get("high")).isCloseTo(1.0, within(1e-9));
    }
}
