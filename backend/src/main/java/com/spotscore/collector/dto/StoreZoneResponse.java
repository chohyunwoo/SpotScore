package com.spotscore.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// TODO: 상권정보 storeListInDong 실제 응답 포맷은 표본 데이터로 검증되지 않았다.
// 아래 구조는 공공데이터포털 공통 응답 규격(header/body) 기준 추정치이다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreZoneResponse(
        @JsonProperty("header") Header header,
        @JsonProperty("body") Body body
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(
            @JsonProperty("resultCode") String resultCode,
            @JsonProperty("resultMsg") String resultMsg
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            @JsonProperty("items") List<StoreItemDto> items,
            @JsonProperty("numOfRows") int numOfRows,
            @JsonProperty("pageNo") int pageNo,
            @JsonProperty("totalCount") int totalCount
    ) {
    }
}
