-- ============================================================================
-- SCORE_CACHE에 age_score(연령적합도) 컬럼을 추가한다. density_score와 동일한
-- 원칙: NEUTRAL 업종(연령적합도 미적용)과 population<100(B-2 최소 인구 기준)
-- 지역x업종 조합은 age_score를 계산하지 않고 null로 둔다 - 임의 추정 금지
-- (CLAUDE.md 연령 구성 지표 섹션). nullable로 추가하므로 기존 행에는 영향 없음.
-- ============================================================================
ALTER TABLE score_cache ADD COLUMN age_score NUMERIC(6, 2);
