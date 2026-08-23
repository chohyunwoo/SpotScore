-- ============================================================================
-- FAVORITE: 사용자가 저장한 관심 "지역 x 업종" 조합. 점수는 항상 지역x업종
-- 조합에 대해서만 의미가 있으므로(CLAUDE.md), 즐겨찾기도 두 코드의 조합을 단위로
-- 저장한다. region_code/industry_code는 기존 코드 테이블 FK로 무결성을 보장하고,
-- (user_id, region_code, industry_code) UNIQUE로 같은 조합을 중복 저장하지 못하게 한다.
--
-- 지역/업종 데이터가 배치 재구축으로 교체될 수 있으므로(REGION 코드 교정 이력 참고)
-- FK는 ON DELETE CASCADE로 두어, 대상 코드가 사라지면 그 즐겨찾기도 함께 정리된다.
-- app_user 삭제 시에도 해당 사용자의 즐겨찾기를 함께 지운다.
-- ============================================================================
CREATE TABLE favorite (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    region_code   VARCHAR(10) NOT NULL REFERENCES region (region_code) ON DELETE CASCADE,
    industry_code VARCHAR(10) NOT NULL REFERENCES industry_category (industry_code) ON DELETE CASCADE,
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_favorite_user_region_industry UNIQUE (user_id, region_code, industry_code)
);

-- 목록 조회는 항상 "이 사용자의 즐겨찾기 전체"(user_id로 필터 + 최신순)라 인덱스를 둔다.
CREATE INDEX idx_favorite_user_id ON favorite (user_id);
