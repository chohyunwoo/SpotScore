package com.spotscore.dto;

import com.spotscore.batch.RegionCoordinateSeedResult;

public record RegionCoordinateSeedResponse(int targetCount, int succeeded, int failed, long elapsedMillis) {

    public static RegionCoordinateSeedResponse from(RegionCoordinateSeedResult result) {
        return new RegionCoordinateSeedResponse(
                result.targetCount(), result.succeeded(), result.failed(), result.elapsedMillis());
    }
}
