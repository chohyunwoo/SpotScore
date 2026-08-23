-- ============================================================================
-- APP_USER: 즐겨찾기(관심 지역x업종) 저장을 위한 일반 사용자 계정 테이블.
-- 지금까지 이 서비스에는 로그인 개념이 없었고 /api/v1/admin/**만 공유 API Key로
-- 막았는데(AdminApiKeyInterceptor), 사용자별 즐겨찾기를 서버에 영속화하려면
-- "누구의 즐겨찾기인지" 식별이 필요해 세션 기반 로그인을 도입한다.
--
-- 테이블명은 'user'가 PostgreSQL 예약어라 app_user로 둔다(따옴표 없이 안전하게
-- 참조하기 위함). password_hash는 BCrypt 해시 문자열(평문 저장 금지) - 애플리케이션
-- 계층(BCryptPasswordEncoder)에서만 다룬다.
-- ============================================================================
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);
