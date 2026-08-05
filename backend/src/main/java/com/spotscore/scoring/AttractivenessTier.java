package com.spotscore.scoring;

/**
 * 퍼센타일 밴드 등급(API 명세서 5.2.3절). percentileRank는 같은 industryCode 내에서
 * "낮을수록 상위"로 계산되므로(0=최상위), 등급 경계도 그 기준으로 나뉜다.
 *
 * 경계값(10/30/70)은 여기 한 곳에서만 관리한다 - 다른 곳에 매직 넘버로 중복하지 말 것.
 */
public enum AttractivenessTier {
    ATTRACTIVE, GOOD, AVERAGE, CAUTION;

    private static final double ATTRACTIVE_UPPER_BOUND_PERCENT = 10.0;
    private static final double GOOD_UPPER_BOUND_PERCENT = 30.0;
    private static final double AVERAGE_UPPER_BOUND_PERCENT = 70.0;

    public static AttractivenessTier fromPercentileRank(double percentileRank) {
        if (percentileRank <= ATTRACTIVE_UPPER_BOUND_PERCENT) {
            return ATTRACTIVE;
        }
        if (percentileRank <= GOOD_UPPER_BOUND_PERCENT) {
            return GOOD;
        }
        if (percentileRank <= AVERAGE_UPPER_BOUND_PERCENT) {
            return AVERAGE;
        }
        return CAUTION;
    }
}
