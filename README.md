# SpotScore

공공데이터(SGIS 통계청 + 소상공인시장진흥공단 상가정보 + KOSIS 국가통계포털) 기반 창업 입지 추천 대시보드.
지역별 업종 경쟁 밀집도와 인구 통계, 업종에 따라서는 연령 구성(연령적합도)까지 조합해
**"지역 × 업종" 조합의 창업 매력도 점수**를 산출한다.

- 백엔드는 전국 대응 구조로 설계, 프론트 UI는 MVP 범위(서울)로 축소
- 점수는 항상 지역과 업종을 함께 지정해야 산출 가능 (지역만으로는 조회 불가)
- 상세 설계/의사결정 배경은 [CLAUDE.md](./CLAUDE.md) 참고

## 기술 스택

| 영역 | 선택 |
| --- | --- |
| 백엔드 | Spring Boot 3 (Gradle, Java 21) |
| 프론트엔드 | React + TypeScript (Vite) |
| DB | PostgreSQL (Docker Compose) + Flyway |
| ORM | Spring Data JPA |
| 인증 | Spring Security (세션 + HttpOnly 쿠키 — 즐겨찾기용 일반 사용자 로그인) |
| 외부 API 호출 | WebClient (SGIS + 상권정보 + KOSIS 비동기 병렬 호출) |
| 배치 | Spring `@Scheduled` (월 1회) |
| 지도 | Kakao Map JS SDK |
| 차트 | Recharts |
| 데이터 페칭 | TanStack Query |
| 스타일링 | styled-components |
| API 문서 | Springdoc OpenAPI (Swagger UI) |
| 배포 | Cloudflare Pages(프론트) · Render(백엔드, Docker) · Supabase(DB) |

## 데이터 흐름

```
[외부 API] SGIS + 상권정보 + KOSIS
      ↓ (배치에서만 호출, 실시간 호출 금지)
[배치] @Scheduled 배치·정규화 (월 1회)
      ↓
[저장] PostgreSQL — 원자료 + 점수 캐시
      ↓
[API] Spring Boot REST — 랭킹/지도/상세
      ↓
[프론트] React — 랭킹 리스트 ↔ 지도 ↔ 상세 패널 (+ 로그인·관심 지역 저장/비교)
```

## 폴더 구조

```
backend/   Spring Boot (Gradle)
  src/main/java/com/spotscore/
    collector/   DataCollector 구현체 (Sgis, StoreZone)
    discovery/   행정구역 코드 크로스워크 재구축
    batch/       @Scheduled 배치 + 코드 매핑 검증
    scoring/     AHP 가중치 기반 점수 계산
    domain/      JPA Entity
    repository/  Spring Data JPA Repository
    controller/  REST 컨트롤러 (+ admin/)
    auth/        회원가입/로그인 서비스 (Spring Security)
    favorite/    즐겨찾기(관심 지역×업종) 서비스
    security/    Security 설정 · CSRF · admin API Key 인터셉터
    dto/
  src/main/resources/db/migration/   Flyway 마이그레이션

frontend/  React (Vite, TypeScript)
  src/
    api/                          REST 클라이언트 (TanStack Query)
    components/
      IndustrySelector/
      MapDashboard/                Kakao Map + 랭킹 리스트 (양방향 연동)
      RegionIndustryDetailPanel/   Recharts 브레이크다운(수요/공급/연령적합도)
      Auth/                        로그인·회원가입 모달 + 상단 인증바
      FavoriteStar/                즐겨찾기 별표 토글(랭킹 행·상세 패널)
      Compare/                     관심 지역 비교 뷰(업종별 그룹)
    context/                       SelectionContext · AuthContext
  functions/api/                   Cloudflare Pages Function — 배포 시 /api/* 를 백엔드로 same-origin 프록시
```

## 로컬 실행

### 1. 사전 준비

- JDK 21, Node 18+, Docker Desktop
- 외부 API 키 발급
  - SGIS(통계청 통계지리정보서비스): https://sgis.kostat.go.kr
  - 소상공인시장진흥공단 상가(상권)정보: https://www.data.go.kr
  - KOSIS(통계청 국가통계포털, 연령별 인구 - 연령적합도 지표): https://kosis.kr/openapi
  - Kakao Map JavaScript 키: Kakao Developers > 내 애플리케이션

### 2. 환경 변수

```bash
cp backend/.env.example backend/.env    # SGIS/상권정보/KOSIS 키, DB 자격증명, ADMIN_API_KEY 입력
cp frontend/.env.example frontend/.env  # API base URL, Kakao Map 키 입력
```

### 3. DB 기동 + 백엔드 실행

```bash
cd backend
docker compose up -d          # PostgreSQL (Flyway가 기동 시 자동 마이그레이션)
./gradlew bootRun             # http://localhost:8080
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- dev 프로파일 기본 배치 대상: 강남구 역삼1동/역삼2동 (env `SPOTSCORE_BATCH_TARGET_REGIONS`로 확장 가능).
  이 값을 비워두면 REGION 테이블에 sgisAdmCd가 매핑된 지역 전체(서울 확장 시 426개)가
  자동으로 배치 대상이 된다 — 지역을 환경변수로 일일이 나열하지 않기 위함.

### 4. 초기 시딩 + 배치 실행 (최초 1회)

`/api/v1/admin/**`은 `X-Admin-Api-Key` 헤더 인증이 필요하다(`.env`의 `ADMIN_API_KEY` 값, 미설정 시 dev는 모든 admin 요청이 401로 차단되고 prod는 부팅 자체가 실패한다).

```bash
export ADMIN_API_KEY=여기에_env의_ADMIN_API_KEY_값

curl -X POST http://localhost:8080/api/v1/admin/industries/seed-featured -H "X-Admin-Api-Key: $ADMIN_API_KEY"   # 업종 드롭다운 노출(featured) 시딩
curl -X POST http://localhost:8080/api/v1/admin/industries/seed-age-direction -H "X-Admin-Api-Key: $ADMIN_API_KEY"  # 업종별 연령적합도 방향성(POSITIVE/NEGATIVE/NEUTRAL) 시딩
curl -X POST http://localhost:8080/api/v1/admin/batch/run -H "X-Admin-Api-Key: $ADMIN_API_KEY"                  # SGIS/상권정보/KOSIS 수집 + 점수 계산
curl -X POST http://localhost:8080/api/v1/admin/regions/seed-coordinates -H "X-Admin-Api-Key: $ADMIN_API_KEY"  # 지도 마커용 좌표 시딩
```

### 5. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev                   # http://localhost:5173 (Kakao Map 키가 이 도메인으로 제한됨)
```

데모 시연 순서는 [frontend/DEMO.md](./frontend/DEMO.md) 참고.

## 주요 API

| Method | Endpoint | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/industries` | 공개 | 업종 목록 (기본 featured=true 30개, `?all=true`로 전체) |
| GET | `/api/v1/scores/ranking?industryCode=` | 공개 | 업종별 지역 랭킹 (점수·퍼센타일·좌표) |
| GET | `/api/v1/scores/detail?regionCode=&industryCode=` | 공개 | 지역×업종 상세 (종합 점수 + 브레이크다운, 업종에 따라 연령적합도 포함) |
| GET | `/api/v1/scores/weights` | 공개 | AHP 가중치 설정 조회 (읽기 전용 — 상세 패널의 가중치 안내 문구가 사용) |
| POST | `/api/v1/auth/register`, `/login`, `/logout` | 공개(부트스트랩)/세션 | 회원가입 · 로그인 · 로그아웃 (세션 + HttpOnly 쿠키) |
| GET | `/api/v1/auth/me` | 세션 | 로그인 사용자 정보 (비로그인 401) |
| GET/POST/DELETE | `/api/v1/favorites`, `/favorites/{id}` | 세션 | 관심 지역×업종 즐겨찾기 목록/추가(멱등)/삭제(본인 소유만) |
| POST | `/api/v1/admin/batch/run` | `X-Admin-Api-Key` | 배치 즉시 실행 |
| POST | `/api/v1/admin/regions/seed-coordinates` | `X-Admin-Api-Key` | 지역 좌표(위경도) 시딩 |
| POST | `/api/v1/admin/regions/discover-seoul` | `X-Admin-Api-Key` | 서울 행정동 크로스워크 탐색 |
| POST | `/api/v1/admin/regions/rebuild-crosswalk` | `X-Admin-Api-Key` | 행정구역 코드 크로스워크 재구축 |
| POST | `/api/v1/admin/scores/recalculate` | `X-Admin-Api-Key` | 점수 재계산 |
| GET/PUT | `/api/v1/admin/score-weights[/{key}]` | `X-Admin-Api-Key` | AHP 가중치 조회(레거시, 공개 조회는 위 `/api/v1/scores/weights` 참고)/수정 |
| POST | `/api/v1/admin/industries/seed-featured` | `X-Admin-Api-Key` | 업종 드롭다운 노출(featured) 목록 시딩 |
| POST | `/api/v1/admin/industries/seed-age-direction` | `X-Admin-Api-Key` | 업종별 연령적합도 방향성(POSITIVE/NEGATIVE/NEUTRAL) 시딩 |

`X-Admin-Api-Key`가 필요한 엔드포인트는 헤더 값이 `.env`의 `ADMIN_API_KEY`와 일치해야 하며, 없거나 틀리면 401을 반환한다. 전체 요청/응답 스키마는 Swagger UI에서 확인.

## 연령적합도(ageScore) 노출 기준

상세 패널의 연령적합도 카드는 모든 업종에 뜨는 게 아니라 **업종별 방향성(`industry_age_direction`)** 에 따라 갈린다.

- 방향성은 `industry_category.industry_code`의 **대분류 접두어**로 정해진다(설정: `spotscore.industry.age-direction.positive-prefixes`/`negative-prefixes`, 기본값 아래).
  코드에 업종을 하드코딩하지 않고 시딩 시점에 계산한다(`IndustryAgeDirectionSeedingService`).

  | 접두어 | 방향 | 의미 | 카드 노출 |
  | --- | --- | --- | --- |
  | `I2`(외식) / `R1`(스포츠·오락) / `P1`(교육) | POSITIVE | 20~39세 비율이 높을수록 유리 | 뜸 |
  | `Q1`(보건의료) | NEGATIVE | 고령층 비율이 높을수록 유리 | 뜸 |
  | 그 외 전부 | NEUTRAL | 연령 구성과 무관 | **안 뜸** (DOM에 없음, 빈 카드 아님) |

- POSITIVE/NEGATIVE 업종이라도, 해당 지역의 인구가 100명 미만이거나 KOSIS 원자료가 아직 수집되지 않았으면 카드는 뜨되 "데이터 부족" placeholder로 표시된다(0점이 아님 — `AgeScoreService` 참고).
- 프론트(`RegionIndustryDetailPanel.tsx`)는 위 접두어를 알지 못하고, API가 내려주는 최상위 `ageDirection` 필드(POSITIVE/NEGATIVE/null)만 보고 렌더링 여부를 결정한다 — 업종 코드를 프론트에 하드코딩하지 않기 위함(아래 확장성 설계 원칙 2).

## 사용자 인증 & 즐겨찾기

관심 있는 "지역 × 업종" 조합을 저장하고 여러 후보지를 나란히 비교하기 위해 **세션 + HttpOnly 쿠키 기반 일반 사용자 로그인**(Spring Security)을 도입했다.

- 공개 조회(랭킹/상세/업종/가중치/업소/챗봇)는 로그인 없이 그대로 이용하고, `/api/v1/favorites/**`·`/api/v1/auth/me`만 인증이 필요하다.
- `/api/v1/admin/**`은 기존 공유 API Key(`X-Admin-Api-Key`)를 그대로 유지 — 세션 로그인과 별개 체계다.
- 비밀번호는 BCrypt 해시로만 저장한다. 상태를 바꾸는 요청(즐겨찾기 추가/삭제, 로그아웃)은 CSRF 토큰(`XSRF-TOKEN` 쿠키 + `X-XSRF-TOKEN` 헤더, 더블 서밋)이 필요하다.
- **비교 뷰**는 즐겨찾기 조합들의 상세 점수를 병렬 조회해 브레이크다운(인구/가구/경쟁 여유도/연령)을 나란히 보여준다. 점수는 업종 내 상대값(퍼센타일)이라 서로 다른 업종을 직접 비교하면 오해를 주므로 **업종별로 그룹화**해 같은 업종끼리만 비교하고 최고값을 강조한다.

## 배포

- **프론트** Cloudflare Pages · **백엔드** Render(Docker) · **DB** Supabase PostgreSQL — 모두 무료 티어.
- **same-origin 프록시**: 프론트와 백엔드가 서로 다른 도메인이면 세션·CSRF 쿠키가 서드파티 쿠키로 취급돼 로그인이 유지되지 않는다. Cloudflare Pages Function(`frontend/functions/api`)이 `/api/*`를 백엔드로 리버스 프록시해 쿠키를 1st-party로 만든다(프론트는 상대경로 `/api/...`로 호출, 배포 빌드에서 `VITE_API_BASE_URL`을 비움).
- **콜드스타트 & keep-warm**(`.github/workflows/keep-warm.yml`): Render 무료 티어는 15분 무활동 시 인스턴스를 내리고 다음 요청은 콜드스타트로 30초~1분 걸린다. GitHub Actions로 활동 시간대에 백엔드를 핑해 깨워두는데, 단순 `*/10` cron은 GitHub이 실행을 지연·누락시켜(실측 34분 갭) 15분 spin-down을 못 막았다. 그래서:
  - **매시 정시 트리거 + 잡 내부 sleep 루프** — "언제 시작하냐"만 GitHub에 맡기고(정시 트리거는 상대적으로 안정적), 실제 핑 간격(5분)은 러너의 `sleep`으로 정확히 보장한다.
  - 각 잡을 **~70분** 돌려 다음 정시 잡과 **~10분 겹치게(overlap)** 해, 트리거가 지연돼도 경계에서 갭이 생기지 않는다(잡끼리 서로 취소하지 않도록 `concurrency`는 두지 않음).
  - 활동 시간대는 **KST 10:00~22:00**(cron은 UTC 기준 `0 1-12`). 12시간 × 2개 서비스 ≈ 월 720h로, Render 무료 **750 instance-hours**(계정 내 무료 서비스 공유) 한도 안에 든다. 이 절충 때문에 **심야(22~10시)엔 첫 접속이 콜드스타트로 느릴 수 있다**.

## 확장성 설계 원칙

1. 데이터 수집기는 `DataCollector` 인터페이스로 분리 — 새 API 소스는 구현체만 추가
2. 지역/업종은 코드 테이블로 관리, 애플리케이션 코드에 하드코딩 금지 ("서울만"은 조회 조건일 뿐)
3. 점수 가중치는 `ScoreWeightConfig` 테이블/설정값으로 분리, 매직 넘버 금지
4. API는 `/api/v1/...` 버전 프리픽스 사용
5. Controller는 Entity를 직접 반환하지 않고 DTO로 변환

자세한 배경과 확정 사항은 [CLAUDE.md](./CLAUDE.md) 참고.
