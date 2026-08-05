package com.spotscore.dto;

import com.spotscore.domain.IndustryCategory;

public record IndustryResponse(String industryCode, String industryName) {

    public static IndustryResponse from(IndustryCategory entity) {
        return new IndustryResponse(entity.getIndustryCode(), entity.getIndustryName());
    }
}
