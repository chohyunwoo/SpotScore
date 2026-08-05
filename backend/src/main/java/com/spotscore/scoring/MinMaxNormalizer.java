package com.spotscore.scoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * min-max 정규화. 비교 대상이 1건뿐이거나 값이 모두 같으면 분모(max-min)가 0이 되므로,
 * 이 경우 예외 대신 WARN 로그 후 중립값(0.5)으로 대체한다 (CLAUDE.md 로깅 가이드 -
 * 정규화 이상치: min-max/z-score 계산 중 분모 0 등 예외).
 */
final class MinMaxNormalizer {

    private static final Logger log = LoggerFactory.getLogger(MinMaxNormalizer.class);
    private static final double NEUTRAL_SCORE = 0.5;

    private MinMaxNormalizer() {
    }

    static <K> Map<K, Double> normalize(Map<K, Double> rawValues, String metricName) {
        if (rawValues.isEmpty()) {
            return Map.of();
        }

        double min = Collections.min(rawValues.values());
        double max = Collections.max(rawValues.values());
        double range = max - min;

        Map<K, Double> result = new LinkedHashMap<>();
        if (range == 0) {
            log.warn("정규화 이상치 - metric: {}, 사유: min-max 분모 0 (비교 대상 {}건 값이 모두 동일), 중립값 {}로 대체",
                    metricName, rawValues.size(), NEUTRAL_SCORE);
            rawValues.keySet().forEach(key -> result.put(key, NEUTRAL_SCORE));
            return result;
        }

        rawValues.forEach((key, value) -> result.put(key, (value - min) / range));
        return result;
    }
}
