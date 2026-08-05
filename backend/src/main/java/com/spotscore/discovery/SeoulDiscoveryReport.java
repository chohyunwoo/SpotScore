package com.spotscore.discovery;

public record SeoulDiscoveryReport(
        int guCount,
        int dongCount,
        int unresolvedGuCount,
        int candidatePairCount,
        int mappingSuccessCount,
        int mappingFailedCount,
        double mappingFailureRatePercent,
        int regionsUpserted,
        long elapsedMillis
) {
}
