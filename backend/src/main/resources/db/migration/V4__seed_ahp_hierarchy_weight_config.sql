-- ============================================================================
-- SCORE_WEIGHT_CONFIG을 CLAUDE.md 5.2.1절 "수요/공급 2축 하이브리드 AHP" 구조에
-- 맞춰 2단계 쌍대비교 값으로 교체한다.
--
-- V1 시드값(POPULATION_SCALE/HOUSEHOLD_STRUCTURE/COMPETITION_DENSITY, 각 1/3)은
-- 리프 3개를 동등하다고 가정한 1단계 임시값이었다. 실제 산출 방법은 한 번에
-- 3개를 비교하지 않고 계층을 나눈다:
--   [종합] = [수요](인구규모, 가구구조) vs [공급](경쟁밀집도)
-- 최종 리프 가중치는 코드(ScoreWeightService)에서
--   population = demandWeight * demandPopulationRatio
--   household  = demandWeight * demandHouseholdRatio
--   competition = supplyWeight
-- 로 계산하므로, 여기서는 리프 가중치가 아니라 쌍대비교 비율 자체를 저장한다.
--
-- TODO(미확정): 아래 값은 팀 쌍대비교 진행 전 임시값(요청대로 5:5 / 5:5)이다.
-- 실제 쌍대비교 완료 후 이 테이블 값만 갱신하면 되고, 코드/스키마 변경은 불필요하다.
-- ============================================================================
DELETE FROM score_weight_config
    WHERE weight_key IN ('POPULATION_SCALE', 'HOUSEHOLD_STRUCTURE', 'COMPETITION_DENSITY');

INSERT INTO score_weight_config (weight_key, weight_value) VALUES
    ('DEMAND_WEIGHT', 0.5),
    ('SUPPLY_WEIGHT', 0.5),
    ('DEMAND_POPULATION_RATIO', 0.5),
    ('DEMAND_HOUSEHOLD_RATIO', 0.5);
