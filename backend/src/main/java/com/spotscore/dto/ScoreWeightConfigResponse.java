package com.spotscore.dto;

import com.spotscore.domain.ScoreWeightConfig;

import java.math.BigDecimal;

public record ScoreWeightConfigResponse(String weightKey, BigDecimal weightValue) {

    public static ScoreWeightConfigResponse from(ScoreWeightConfig entity) {
        return new ScoreWeightConfigResponse(entity.getWeightKey(), entity.getWeightValue());
    }
}
