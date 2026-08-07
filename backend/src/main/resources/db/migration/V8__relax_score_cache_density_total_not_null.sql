-- ============================================================================
-- densityScore 계산 방식을 B-1(인구 대비 밀도 min-max 정규화)에서 B-2(퍼센타일
-- 랭크 + 최소 인구 기준)로 교체하면서, 인구 100명 미만 지역은 해당 업종에 대해
-- densityScore/totalScore를 임의로 추정하지 않고 NULL로 응답하기로 함
-- (창업매력도_정의_재검토_기록.md 10절 - B-1은 극단 이상치(인구 19명 지역)
-- 하나가 min-max 스케일 전체를 왜곡하는 결함이 발견되어 교체).
--
-- population_score/household_score는 지역 단위 값이라 이번 변경과 무관하게
-- 계속 NOT NULL 유지 - total_score/density_score만 nullable로 완화한다
-- (DB_스키마_변경_관리_가이드.md "nullable 우선" 원칙).
-- ============================================================================
ALTER TABLE score_cache
    ALTER COLUMN total_score DROP NOT NULL;

ALTER TABLE score_cache
    ALTER COLUMN density_score DROP NOT NULL;
