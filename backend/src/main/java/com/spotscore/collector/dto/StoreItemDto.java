package com.spotscore.collector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 필드명은 실제 storeListInDong 응답으로 검증 완료(2026-08-04, 강남구 역삼1/2동 표본).
// indsMclsCd/indsMclsNm은 store_count 배치 적재 시 industry_category 시드용으로 쓴다.
// 상권업종분류(indsMclsCd)와 표준산업분류(ksicCd)는 서로 다른 코드로 응답에 둘 다
// 존재함을 확인했으나, 둘을 통일할지 여부는 여전히 팀 논의 필요(CLAUDE.md 미확정 사항).
// signguCd/signguNm은 4주차 서울 전체 매핑 확장(SeoulRegionDiscoveryService)에서
// divId=ctprvnCd로 조회할 때 공식 시군구코드를 수집하는 데 쓴다 - 이미 실제 응답으로
// 확인된 필드다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreItemDto(
        @JsonProperty("bizesId") String storeId,
        @JsonProperty("bizesNm") String storeName,
        @JsonProperty("indsLclsCd") String industryLargeCode,
        @JsonProperty("indsMclsCd") String industryMediumCode,
        @JsonProperty("indsMclsNm") String industryMediumName,
        @JsonProperty("indsSclsCd") String industrySmallCode,
        @JsonProperty("adongCd") String adongCode,
        @JsonProperty("adongNm") String adongName,
        @JsonProperty("signguCd") String signguCode,
        @JsonProperty("signguNm") String signguName
) {
}
