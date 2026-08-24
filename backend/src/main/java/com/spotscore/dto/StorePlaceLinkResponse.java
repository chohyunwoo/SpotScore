package com.spotscore.dto;

/**
 * GET /api/v1/stores/{bizesId}/place-link 응답. placeUrl은 Kakao Local 검색으로 찾은
 * 카카오맵 장소 상세 URL - 키 미설정/결과 없음이면 null이며, 이때 프론트는 이름 검색
 * 링크로 폴백한다(이슈 #34).
 */
public record StorePlaceLinkResponse(String placeUrl) {
}
