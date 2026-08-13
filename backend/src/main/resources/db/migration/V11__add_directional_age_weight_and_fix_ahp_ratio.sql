-- ============================================================================
-- AHP v3: 연령적합도(ageScore)를 사용하는 업종(POSITIVE/NEGATIVE, "DIRECTIONAL"
-- 그룹)을 위한 최상위 축(CORE_WEIGHT/AGE_WEIGHT=3:1)을 추가한다. NEUTRAL 업종은
-- 기존 DEMAND_WEIGHT/SUPPLY_WEIGHT/POPULATION_RATIO/HOUSEHOLD_RATIO 4개를 그대로
-- 쓰고, DIRECTIONAL 업종만 이 값들에 CORE_WEIGHT를 추가로 곱한 뒤 AGE_WEIGHT를
-- 더한다 - 리프 값을 그룹별로 중복 저장하지 않고 기존 쌍대비교 구조를 그대로
-- 재사용한다(CLAUDE.md 가중치 산출 방법 v2/연령 구성 지표 섹션).
--
-- weight_group은 이 행이 어떤 업종 그룹에서 쓰이는지 나타내는 메타데이터다:
--   COMMON      - NEUTRAL/DIRECTIONAL 둘 다 공유(그룹별로 중복 저장하지 않음)
--   DIRECTIONAL - DIRECTIONAL 그룹(POSITIVE/NEGATIVE 업종)에서만 쓰임
-- 실제 리프 가중치 계산 공식은 ScoreWeightService 참고.
--
-- 같이 포함하는 버그 수정: DEMAND_POPULATION_RATIO/DEMAND_HOUSEHOLD_RATIO가 V4에서
-- 팀 쌍대비교 전 임시값(0.5/0.5)으로 남아 있었음 - CLAUDE.md에 확정된 3:1
-- (인구규모 우위, Saaty 3)로 교체한다. 이 수정만으로 NEUTRAL 그룹의 populationScore/
-- householdScore가 바뀐다(0.5/0.5 -> 0.75/0.25 비율 반영, SCORE_CACHE 재계산 필요).
-- 키 이름도 DEMAND_ 접두어를 떼어 POPULATION_RATIO/HOUSEHOLD_RATIO로 정리한다
-- (수요 내부 비율이라는 의미는 동일, DIRECTIONAL 그룹에서도 그대로 재사용되므로
-- "DEMAND" 접두어가 오히려 오해를 줌).
-- ============================================================================
ALTER TABLE score_weight_config ADD COLUMN weight_group VARCHAR(20);

UPDATE score_weight_config
    SET weight_key = 'POPULATION_RATIO', weight_value = 0.75, weight_group = 'COMMON'
    WHERE weight_key = 'DEMAND_POPULATION_RATIO';

UPDATE score_weight_config
    SET weight_key = 'HOUSEHOLD_RATIO', weight_value = 0.25, weight_group = 'COMMON'
    WHERE weight_key = 'DEMAND_HOUSEHOLD_RATIO';

UPDATE score_weight_config
    SET weight_group = 'COMMON'
    WHERE weight_key IN ('DEMAND_WEIGHT', 'SUPPLY_WEIGHT');

INSERT INTO score_weight_config (weight_key, weight_value, weight_group) VALUES
    ('CORE_WEIGHT', 0.75, 'DIRECTIONAL'),
    ('AGE_WEIGHT', 0.25, 'DIRECTIONAL');
