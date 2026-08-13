-- ============================================================================
-- AGE_STAT: KOSIS(통계청) 주민등록인구현황(DT_1B04005N, 행정구역(읍면동)별/5세별
-- 주민등록인구) 배치 적재 결과. 20~39세 인구(5세 구간 4개 합)와 KOSIS 자체
-- 총인구를 저장한다.
--
-- kosis_total_population은 population_stat.total_population(SGIS 추계인구)과
-- 통계 기준 자체가 다른 별개 수치다(전자는 주민등록 기준, 후자는 추계 기준) -
-- 어떤 계산에서도 섞어 쓰지 않는다는 원칙을 컬럼 분리로 스키마 레벨에서도
-- 강제한다(CLAUDE.md 연령 구성 지표 섹션).
-- ============================================================================
CREATE TABLE age_stat (
    id                     BIGSERIAL PRIMARY KEY,
    region_code            VARCHAR(10) NOT NULL REFERENCES region (region_code),
    year                   INT NOT NULL,
    age2039_cnt            BIGINT,
    kosis_total_population BIGINT,
    snapshot_date          DATE NOT NULL,
    CONSTRAINT uq_age_stat_region_year UNIQUE (region_code, year)
);

-- ============================================================================
-- INDUSTRY_AGE_DIRECTION: 업종별 "20~39세 비중이 높을수록 유리한 업종인지"
-- 방향성. CLAUDE.md 확장성 원칙(업종 코드 하드코딩 금지)에 따라 코드에 나열하지
-- 않고 별도 코드 테이블로 관리하며, 실제 값은 대분류 접두어 기준 시딩 로직
-- (IndustryAgeDirectionSeedingService)이 채운다 - 이 마이그레이션은 스키마만
-- 추가한다(FeaturedIndustrySeedingService/V7과 동일하게 스키마와 데이터 시딩을 분리).
-- ============================================================================
CREATE TABLE industry_age_direction (
    industry_code VARCHAR(10) PRIMARY KEY REFERENCES industry_category (industry_code),
    direction     VARCHAR(10) NOT NULL CHECK (direction IN ('POSITIVE', 'NEGATIVE', 'NEUTRAL'))
);
