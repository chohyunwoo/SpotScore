package com.spotscore.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Kakao Local 키워드 검색(/v2/local/search/keyword.json) 응답 중 이 프로젝트가 쓰는
 * 필드만 선언(place_url = 카카오맵 장소 상세 페이지 URL). 나머지 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoKeywordSearchResponse(List<Document> documents) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            @JsonProperty("place_name") String placeName,
            @JsonProperty("place_url") String placeUrl
    ) {
    }
}
