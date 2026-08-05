package com.spotscore.collector.dto;

/**
 * SGIS boundary/hadmarea.geojson 응답 1건을 centroid 계산까지 마친 결과.
 * latitude/longitude는 WGS84(degrees) 기준이다(Epsg5179ToWgs84Transformer 참고).
 */
public record BoundaryCentroidDto(String admCd, String admNm, double latitude, double longitude) {
}
