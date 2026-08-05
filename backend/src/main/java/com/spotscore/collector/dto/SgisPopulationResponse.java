package com.spotscore.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SgisPopulationResponse(
        @JsonProperty("errCd") int errCd,
        @JsonProperty("errMsg") String errMsg,
        @JsonProperty("result") List<SgisPopulationDto> result
) {
}
