package com.spotscore.collector.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Epsg5179ToWgs84TransformerTest {

    // 실제 SGIS boundary/hadmarea.geojson 응답(강남구 역삼1동, adm_cd=11230640)의
    // properties.x/y 값 - 실제 위경도(약 위도 37.50, 경도 127.03)와 맞는지 검증한다.
    @Test
    void convertsYeoksam1DongCentroidIntoPlausibleSeoulCoordinates() {
        Epsg5179ToWgs84Transformer transformer = new Epsg5179ToWgs84Transformer();

        double[] latLng = transformer.toWgs84(958643, 1944720);

        assertThat(latLng[0]).as("latitude").isBetween(37.4, 37.6);
        assertThat(latLng[1]).as("longitude").isBetween(126.9, 127.1);
    }
}
