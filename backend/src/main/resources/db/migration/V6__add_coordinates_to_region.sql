-- ============================================================================
-- REGION에 지도 마커 좌표(latitude/longitude) 컬럼을 추가한다.
--
-- 값은 실시간 상권정보 호출이 아니라 SGIS boundary/hadmarea.geojson(행정구역
-- 경계 폴리곤) 응답의 centroid를 1회성으로 계산해 채운다 - 경계는 거의 바뀌지
-- 않으므로 매월 배치에 넣지 않고 별도 시딩 로직(RegionCoordinateSeedingService)
-- 으로 처리한다. 그래서 nullable로 둔다: 시딩 전에는 null, 시딩 후 채워진다.
-- ============================================================================
ALTER TABLE region
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION;
