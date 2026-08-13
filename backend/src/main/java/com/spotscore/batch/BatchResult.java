package com.spotscore.batch;

/**
 * 배치 1회 실행 결과 요약. 수동 트리거 엔드포인트 응답과 로그 요약에 함께 쓴다.
 */
public record BatchResult(
        int targetRegionCount,
        int regionsCollected,
        int regionsSkipped,
        int populationRowsSaved,
        int storeCountRowsSaved,
        int ageStatRowsSaved,
        long elapsedMillis
) {
}
