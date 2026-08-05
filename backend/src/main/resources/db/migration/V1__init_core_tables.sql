-- ============================================================================
-- REGION / INDUSTRY_CATEGORY: 코드 테이블. 전국 코드를 적재하되, 서비스 조회 시
-- "서울만" 제약은 WHERE 조건/설정값으로 처리한다 (CLAUDE.md 확장성 원칙 2).
--
-- TODO(미확정): SGIS 행정동 코드(adm_cd, 7자리로 알려짐)와 상권정보 adongCd의
-- 자릿수/포맷이 실제 표본 데이터로 아직 대조되지 않았다. region_code를
-- VARCHAR(10)으로 넉넉히 잡아두고, 실제 매핑 검증 후 필요 시 CHECK 제약을
-- 추가한다.
-- ============================================================================
CREATE TABLE region (
    region_code VARCHAR(10) PRIMARY KEY,
    region_name VARCHAR(100) NOT NULL,
    level       VARCHAR(20) NOT NULL
);

-- TODO(미확정): 상권업종분류와 표준산업분류 불일치 시 업종 통일 기준이 아직
-- 정해지지 않았다. 우선 상권정보 API의 업종 코드 체계를 그대로 적재한다.
CREATE TABLE industry_category (
    industry_code VARCHAR(10) PRIMARY KEY,
    industry_name VARCHAR(100) NOT NULL,
    level         VARCHAR(20) NOT NULL
);

-- ============================================================================
-- POPULATION_STAT: SGIS stats/population.json 배치 적재 결과.
--
-- TODO(미확정): 대시보드 브레이크다운은 "인구 규모"와 "가구 구조"를 별도 항목으로
-- 요구하지만(CLAUDE.md 대시보드 화면 구성/가중치 산출 방법), CLAUDE.md에 명시된
-- 핵심 엔티티 스키마에는 가구 구조를 나타내는 컬럼이 없다. SGIS 응답에서 가구 수를
-- 어떤 필드로 받아 어느 컬럼에 적재할지 팀 논의 후 마이그레이션을 추가한다.
--
-- TODO(미확정): SGIS 통계값이 5 이하일 때 비공개(N/A)로 내려오는 케이스를 0으로
-- 볼지, 행 자체를 제외할지 정해지지 않았다. 우선 NULL 허용으로 두고 정규화 단계
-- (2주차)에서 정책이 정해지면 반영한다.
-- ============================================================================
CREATE TABLE population_stat (
    id                BIGSERIAL PRIMARY KEY,
    region_code       VARCHAR(10) NOT NULL REFERENCES region (region_code),
    year              INT NOT NULL,
    total_population  BIGINT,
    density           NUMERIC(14, 4),
    CONSTRAINT uq_population_stat_region_year UNIQUE (region_code, year)
);

-- ============================================================================
-- STORE_COUNT: 소상공인시장진흥공단 상가(상권)정보 storeListInDong 배치 적재 결과.
-- 월 1회 배치이므로 snapshot_date로 시점을 구분한다.
-- ============================================================================
CREATE TABLE store_count (
    id             BIGSERIAL PRIMARY KEY,
    region_code    VARCHAR(10) NOT NULL REFERENCES region (region_code),
    industry_code  VARCHAR(10) NOT NULL REFERENCES industry_category (industry_code),
    store_count    INT NOT NULL,
    snapshot_date  DATE NOT NULL,
    CONSTRAINT uq_store_count_region_industry_snapshot UNIQUE (region_code, industry_code, snapshot_date)
);

-- ============================================================================
-- SCORE_WEIGHT_CONFIG: 수요/공급 2축 하이브리드 AHP로 산출한 최종 가중치.
-- 매직 넘버 하드코딩 금지 - 계산식은 이 테이블에서 weight_key로 조회한다.
--
-- TODO(미확정): 실제 쌍대비교 수치는 팀 논의 전이라 미정. 아래 기본값은 세
-- 항목이 동등하다고 가정한 임시값(각 1/3)이며, 쌍대비교 완료 후 갱신한다.
-- ============================================================================
CREATE TABLE score_weight_config (
    config_id     BIGSERIAL PRIMARY KEY,
    weight_key    VARCHAR(50) NOT NULL UNIQUE,
    weight_value  NUMERIC(6, 4) NOT NULL
);

INSERT INTO score_weight_config (weight_key, weight_value) VALUES
    ('POPULATION_SCALE', 0.3333),
    ('HOUSEHOLD_STRUCTURE', 0.3333),
    ('COMPETITION_DENSITY', 0.3334);

-- ============================================================================
-- SCORE_CACHE: 지역 x 업종 조합별 산출된 종합/브레이크다운 점수 캐시.
-- population_score/density_score 명칭은 CLAUDE.md 핵심 엔티티 스키마를 그대로
-- 따른다.
--
-- TODO(미확정): 가중치 산출 방법과 마찬가지로, 브레이크다운은 인구 규모/가구
-- 구조/경쟁 밀집도 3항목인데 이 스키마에는 population_score, density_score
-- 2개만 존재한다. 가구 구조 점수를 별도 컬럼으로 캐싱할지, population_score에
-- 합산할지는 2주차 점수 계산 로직 구현 시 팀 논의 후 확정한다.
-- ============================================================================
CREATE TABLE score_cache (
    id                 BIGSERIAL PRIMARY KEY,
    region_code        VARCHAR(10) NOT NULL REFERENCES region (region_code),
    industry_code      VARCHAR(10) NOT NULL REFERENCES industry_category (industry_code),
    total_score        NUMERIC(6, 2) NOT NULL,
    population_score   NUMERIC(6, 2) NOT NULL,
    density_score      NUMERIC(6, 2) NOT NULL,
    calculated_at       TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_score_cache_region_industry UNIQUE (region_code, industry_code)
);
