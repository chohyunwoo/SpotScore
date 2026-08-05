-- ============================================================================
-- POPULATION_STAT에 가구 구조 컬럼 추가 (V1 TODO 해소).
-- 대시보드 브레이크다운 ②가구 구조(CLAUDE.md 대시보드 화면 구성) 산출을 위해
-- SGIS stats/population.json 응답의 가구 관련 필드를 별도 컬럼으로 적재한다.
--
-- TODO(미확정): SGIS 응답 필드명(가구수/평균가구원수에 해당하는 실제 키)은 아직
-- 표본 데이터로 검증되지 않았다 (SgisPopulationDto 참고). 컬럼 자체는 확정하되,
-- 수집 시 매핑하는 응답 필드명은 실제 응답 확인 후 collector에서 조정한다.
-- ============================================================================
ALTER TABLE population_stat
    ADD COLUMN total_family BIGINT,
    ADD COLUMN avg_family_member_count DOUBLE PRECISION;
