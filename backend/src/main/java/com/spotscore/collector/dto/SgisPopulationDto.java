package com.spotscore.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 필드명은 실제 stats/population.json 응답으로 검증 완료(2026-08-04, year 필수
// 파라미터 확인 포함). 값이 5 이하로 비공개 처리된 필드는 문자열 "N/A"로 내려온다
// (SgisValueParser가 파싱 실패로 감지해 WARN + null 처리).
@JsonIgnoreProperties(ignoreUnknown = true)
public record SgisPopulationDto(
        @JsonProperty("adm_cd") String admCd,
        @JsonProperty("adm_nm") String admNm,
        @JsonProperty("tot_ppltn") String totalPopulation,
        @JsonProperty("ppltn_dnsty") String populationDensity,
        @JsonProperty("tot_family") String totalFamily,
        @JsonProperty("avg_fmember_cnt") String avgFamilyMemberCount
) {
}
