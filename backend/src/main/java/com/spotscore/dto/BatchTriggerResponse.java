package com.spotscore.dto;

import com.spotscore.batch.BatchResult;

public record BatchTriggerResponse(
        int targetRegionCount,
        int regionsCollected,
        int regionsSkipped,
        int populationRowsSaved,
        int storeCountRowsSaved,
        int ageStatRowsSaved,
        long elapsedMillis
) {

    public static BatchTriggerResponse from(BatchResult result) {
        return new BatchTriggerResponse(
                result.targetRegionCount(),
                result.regionsCollected(),
                result.regionsSkipped(),
                result.populationRowsSaved(),
                result.storeCountRowsSaved(),
                result.ageStatRowsSaved(),
                result.elapsedMillis()
        );
    }
}
