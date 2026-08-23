package com.spotscore.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 즐겨찾기 추가 요청. 점수는 항상 지역x업종 조합에 대해서만 의미가 있으므로
 * (CLAUDE.md) 두 코드를 모두 받는다.
 */
public record FavoriteRequest(
        @NotBlank(message = "지역 코드는 필수입니다.")
        String regionCode,

        @NotBlank(message = "업종 코드는 필수입니다.")
        String industryCode) {
}
