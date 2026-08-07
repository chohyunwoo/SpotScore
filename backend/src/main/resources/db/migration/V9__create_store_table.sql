-- ============================================================================
-- STORE: 개별 업소(가게) 원본 데이터. 지도에 개별 마커를 찍기 위해 상권정보
-- storeListInDong 응답의 집계 이전 원본 행을 그대로 보관한다.
--
-- STORE_COUNT(지역x업종별 집계, 점수 계산 원자료)는 이 작업으로 손대지 않는다 -
-- STORE는 순수 조회(지도 마커) 전용 추가 테이블이다.
--
-- bizes_id(상가업소번호)는 상권정보 API가 부여하는 안정적인 전국 유일 식별자라
-- 그대로 PK로 쓴다 - snapshot_date는 이력이 아니라 "가장 최근에 확인된 시점"만
-- 값으로 남긴다(재실행 시 같은 bizes_id는 갱신).
--
-- lon/lat: 실제 storeListInDong 응답으로 확인 완료(2026-08-07, 강남구 역삼2동
-- 표본) - 이미 WGS84 십진수 좌표로 내려온다(예: lon=127.05, lat=37.49 수준의
-- 값). SGIS boundary/hadmarea.geojson과 달리 EPSG:5179 변환이 필요 없다.
-- 응답에 좌표가 없는 극소수 케이스를 대비해 nullable로 둔다.
-- ============================================================================
CREATE TABLE store (
    bizes_id      VARCHAR(30) PRIMARY KEY,
    bizes_nm      VARCHAR(200) NOT NULL,
    inds_mcls_cd  VARCHAR(10) NOT NULL REFERENCES industry_category (industry_code),
    inds_mcls_nm  VARCHAR(100) NOT NULL,
    region_code   VARCHAR(10) NOT NULL REFERENCES region (region_code),
    lon           DOUBLE PRECISION,
    lat           DOUBLE PRECISION,
    snapshot_date DATE NOT NULL
);

-- GET /api/v1/stores?regionCode=&industryCode= 조회 패턴에 맞춘 인덱스.
CREATE INDEX idx_store_region_industry ON store (region_code, inds_mcls_cd);
