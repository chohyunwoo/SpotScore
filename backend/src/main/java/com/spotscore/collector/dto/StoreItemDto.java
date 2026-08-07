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
//
// lon/lat: STORE 테이블(지도 개별 마커) 도입 시 실제 응답으로 재검증 완료(2026-08-07,
// 강남구 역삼2동 표본, 예: lon=127.05173662026/lat=37.497275529502) - 이미 WGS84
// 십진수 좌표로 내려온다. SGIS boundary/hadmarea.geojson과 달리 EPSG:5179가 아니므로
// proj4j 변환이 필요 없다. 극소수 케이스에서 좌표가 없을 수 있어 nullable로 둔다.
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
        @JsonProperty("signguNm") String signguName,
        @JsonProperty("lon") Double lon,
        @JsonProperty("lat") Double lat
) {
}
