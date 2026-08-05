package com.spotscore.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// TODO: SGIS auth/authentication.json 실제 응답 필드는 표본 데이터로 검증되지 않았다.
// 아래 필드명은 공식 문서 기준 추정치이므로 실제 응답과 대조 후 수정할 것.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SgisAuthResponse(
        @JsonProperty("errCd") int errCd,
        @JsonProperty("errMsg") String errMsg,
        @JsonProperty("result") Result result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("accessToken") String accessToken,
            @JsonProperty("accessTimeout") String accessTimeout
    ) {
    }
}
