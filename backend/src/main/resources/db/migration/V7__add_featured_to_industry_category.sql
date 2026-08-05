-- ============================================================================
-- 추천 업종(featured) 플래그 추가. 상위 몇 개를 "추천"으로 보여줄지는 실제 업소
-- 수 집계 결과에 따라 달라지는 데이터 값이라 스키마와 분리한다 - 이 마이그레이션은
-- 컬럼만 추가하고, 실제 어떤 업종을 featured=true로 채울지는 별도 시딩 로직
-- (FeaturedIndustrySeedingService)에서 처리한다.
-- ============================================================================
ALTER TABLE industry_category ADD COLUMN featured BOOLEAN NOT NULL DEFAULT false;
