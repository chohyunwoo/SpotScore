package com.spotscore.dto;

import com.spotscore.domain.Store;

/**
 * GET /api/v1/stores 응답 항목 - 상세 패널을 열었을 때만 호출되는 지역x업종
 * 개별 업소 목록(지도 마커용). 전체 랭킹 지도에서는 호출하지 않는다(성능 고려).
 *
 * 출처: 소상공인시장진흥공단 상가(상권)정보(공공데이터포털).
 */
public record StoreItemResponse(
        String bizesId,
        String bizesNm,
        Double lon,
        Double lat
) {

    public static StoreItemResponse from(Store store) {
        return new StoreItemResponse(store.getBizesId(), store.getBizesNm(), store.getLon(), store.getLat());
    }
}
