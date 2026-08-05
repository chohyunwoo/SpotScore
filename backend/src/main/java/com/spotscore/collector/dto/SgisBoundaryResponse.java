package com.spotscore.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

// 실제 boundary/hadmarea.geojson 응답으로 검증된 필드명(2026-08-04, 강남구 역삼1동
// 표본). errCd/errMsg는 다른 SGIS 엔드포인트와 동일한 최상위 위치에 있다.
// coordinates는 geometry.type에 따라 중첩 구조가 달라 JsonNode로 받아 직접 판별한다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SgisBoundaryResponse(
        @JsonProperty("errCd") int errCd,
        @JsonProperty("errMsg") String errMsg,
        @JsonProperty("features") List<Feature> features
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(
            @JsonProperty("geometry") Geometry geometry,
            @JsonProperty("properties") Properties properties
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(
            @JsonProperty("type") String type,
            @JsonProperty("coordinates") JsonNode coordinates
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            @JsonProperty("adm_cd") String admCd,
            @JsonProperty("adm_nm") String admNm
    ) {
    }
}
