package com.spotscore.scoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL {@code PERCENT_RANK() OVER (ORDER BY value ASC)}와 동일한 정의로 퍼센타일을
 * 계산한다: percentile = (자신보다 작은 값의 개수) / (N - 1). ScoreCacheRepository.
 * findRankingWithPercentile / AttractivenessTier가 이미 이 정의로 등급을 매기고
 * 있어 그 패턴을 그대로 재사용한다 - min-max 정규화(MinMaxNormalizer)와 달리
 * 극단 이상치의 "값 크기"가 아니라 "순위"만 반영하므로, 이상치 하나가 전체
 * 스케일을 왜곡하지 않는다.
 *
 * 이번 배치에서 아직 SCORE_CACHE에 커밋되지 않은 값을 대상으로 계산해야 하므로
 * (배치 도중에는 DB에 없는 값), DB 윈도우 함수 대신 동일한 정의를 자바로 구현한다.
 */
final class PercentileRankNormalizer {

    private static final Logger log = LoggerFactory.getLogger(PercentileRankNormalizer.class);

    private PercentileRankNormalizer() {
    }

    static <K> Map<K, Double> ascendingPercentRank(Map<K, Double> rawValues, String metricName) {
        if (rawValues.isEmpty()) {
            return Map.of();
        }

        int n = rawValues.size();
        Map<K, Double> result = new LinkedHashMap<>();

        if (n == 1) {
            log.warn("퍼센타일 계산 - metric: {}, 비교 대상 1건뿐이라 PERCENT_RANK 정의상 0으로 처리", metricName);
            rawValues.keySet().forEach(key -> result.put(key, 0.0));
            return result;
        }

        // PERCENT_RANK 정의상 각 값의 순위 = "자신보다 작은 값의 개수"인데, 이는
        // 오름차순 정렬 배열에서 그 값이 처음 등장하는 인덱스와 같다(동점은 모두
        // 같은 순위). 그래서 정렬 1회(O(n log n)) 후 "값 -> 첫 등장 인덱스"를 한 번
        // 훑어 만들어 두면, 각 key는 조회(O(1))만 하면 된다. 이전엔 key마다 전체를
        // 다시 스캔(filter)해 O(n^2)였다.
        List<Double> sortedValues = rawValues.values().stream().sorted().toList();
        Map<Double, Long> countLessByValue = new HashMap<>();
        for (int i = 0; i < sortedValues.size(); i++) {
            countLessByValue.putIfAbsent(sortedValues.get(i), (long) i);
        }

        rawValues.forEach((key, value) ->
                result.put(key, countLessByValue.get(value) / (double) (n - 1)));
        return result;
    }
}
