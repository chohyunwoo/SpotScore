package com.spotscore.scoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 퍼센타일 밴드 등급 경계(10/30/70)를 검증한다(API 명세서 5.2.3절).
 * percentileRank는 "낮을수록 상위"(0=최상위)라 경계가 이하(<=) 기준으로 닫힌다.
 * 경계값이 어느 쪽 등급에 속하는지는 회귀가 나기 쉬운 지점이라 정확히 고정한다.
 */
class AttractivenessTierTest {

    @ParameterizedTest
    @CsvSource({
            "0.0,   ATTRACTIVE",
            "9.99,  ATTRACTIVE",
            "10.0,  ATTRACTIVE",   // 경계 10은 상위(ATTRACTIVE)에 포함(<=)
            "10.01, GOOD",
            "29.99, GOOD",
            "30.0,  GOOD",         // 경계 30은 GOOD에 포함
            "30.01, AVERAGE",
            "69.99, AVERAGE",
            "70.0,  AVERAGE",      // 경계 70은 AVERAGE에 포함
            "70.01, CAUTION",
            "99.99, CAUTION",
            "100.0, CAUTION"
    })
    void classifiesByPercentileBoundaries(double percentileRank, AttractivenessTier expected) {
        assertThat(AttractivenessTier.fromPercentileRank(percentileRank)).isEqualTo(expected);
    }

    @Test
    void topRankIsAttractiveAndBottomIsCaution() {
        assertThat(AttractivenessTier.fromPercentileRank(0.0)).isEqualTo(AttractivenessTier.ATTRACTIVE);
        assertThat(AttractivenessTier.fromPercentileRank(100.0)).isEqualTo(AttractivenessTier.CAUTION);
    }
}
