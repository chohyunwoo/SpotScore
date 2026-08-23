package com.spotscore.dto;

import com.spotscore.domain.Favorite;

import java.time.LocalDateTime;

/**
 * 즐겨찾기 목록/추가 응답. 지역명·업종명을 함께 내려, 프론트가 비교 뷰 목록을
 * 코드→이름 재조회 없이 바로 렌더링하고, 각 항목의 상세 점수는 기존
 * /api/v1/scores/detail 엔드포인트로 조회하도록 한다.
 */
public record FavoriteResponse(
        Long id,
        String regionCode,
        String regionName,
        String industryCode,
        String industryName,
        LocalDateTime createdAt) {

    public static FavoriteResponse from(Favorite favorite) {
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getRegion().getRegionCode(),
                favorite.getRegion().getRegionName(),
                favorite.getIndustry().getIndustryCode(),
                favorite.getIndustry().getIndustryName(),
                favorite.getCreatedAt());
    }
}
