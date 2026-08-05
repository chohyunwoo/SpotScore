-- ============================================================================
-- SCORE_CACHE에 household_score 컬럼 추가 (V1 TODO 해소).
--
-- CLAUDE.md 대시보드 화면 구성은 상세 패널 브레이크다운을 ①인구 규모 ②가구 구조
-- ③경쟁 밀집도 3항목으로 명시하고, 가중치 산출 방법(5.2.1)도 수요 쪽을 인구 규모/
-- 가구 구조 2개 리프로 나눈다. V1 스키마는 population_score/density_score 2개뿐이라
-- 가구 구조 점수를 별도로 캐싱할 수 없었다 (V1 TODO). 두 값을 하나로 합치면 상세
-- 패널이 "왜 이 점수인가"를 3항목으로 보여줘야 하는 요구를 만족할 수 없으므로,
-- 합산 대신 별도 컬럼으로 분리한다.
-- ============================================================================
ALTER TABLE score_cache
    ADD COLUMN household_score NUMERIC(6, 2);

UPDATE score_cache SET household_score = 0 WHERE household_score IS NULL;

ALTER TABLE score_cache
    ALTER COLUMN household_score SET NOT NULL;
