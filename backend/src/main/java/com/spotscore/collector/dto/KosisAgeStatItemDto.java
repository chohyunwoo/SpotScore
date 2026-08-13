package com.spotscore.collector.dto;

/**
 * KOSIS Param/statisticsParameterData.do 응답 1행. 실제 응답은 성공 시 최상위가
 * 배열, 실패 시 {"err":..,"errMsg":..} 객체로 형태 자체가 바뀌어(SGIS/StoreZone
 * 같은 고정 envelope가 없음) KosisAgeCollector가 JsonNode로 먼저 성공/실패를
 * 분기한 뒤 배열일 때만 이 타입으로 수동 매핑한다.
 *
 * ageCode(C2): "0"=계(총인구), "25"/"30"/"35"/"40"=20-24/25-29/30-34/35-39세
 * 5세 구간(실제 라이브 호출로 검증 완료, 2026-08-13).
 */
public record KosisAgeStatItemDto(String regionCode, String ageCode, String value) {
}
