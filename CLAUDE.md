# CLAUDE.md

## 프로젝트 개요

**공공데이터 기반 창업 입지 추천 대시보드** — 예비 창업자를 타겟으로, 지역별 업종 밀집도(경쟁 강도) + 인구 통계를 조합해 "창업 매력도 점수"를 산출하는 서비스.

- 형식: 개인 프로젝트 (약 1개월, 기획~구현)
- 핵심 원칙: **백엔드는 전국 대응으로 설계, 프론트 UI는 MVP 범위(서울)로 축소**
- 점수는 항상 **"지역 × 업종" 조합**에 대해서만 계산 가능 (지역만으로는 점수 산출 불가)

## 확정 기술 스택


| 영역         | 선택                            | 비고                                                   |
| ---------- | ----------------------------- | ---------------------------------------------------- |
| 백엔드        | Spring Boot (Gradle)          | Java 경험은 있으나 Python 경험 없음 → Python 스택 배제하고 Spring 선택 |
| 프론트엔드      | React                         |                                                      |
| DB         | PostgreSQL                    | 관계형 구조(지역 × 업종 = 점수), 추후 PostGIS 확장 가능               |
| 외부 API 호출  | WebClient                     | 비동기 병렬 호출(SGIS + 상권정보)                               |
| 배치 스케줄링    | Spring `@Scheduled`           | 월 1회 배치                                              |
| DB 접근(ORM) | Spring Data JPA               |                                                      |
| 지도 시각화     | Kakao Map API                 |                                                      |
| 차트(브레이크다운) | Recharts                      |                                                      |
| 데이터 페칭     | TanStack Query                |                                                      |
| 상태관리       | React Context API             |                                                      |
| DB 실행 환경   | Docker Compose                | 환경 간 동일 DB 버전/설정 재현                                  |
| 스키마 마이그레이션 | Flyway                        | 스키마 변경 이력 추적, 확장성 원칙 준수 검증                           |
| 환경 분리      | Spring Profile (dev/prod)     |                                                      |
| 백엔드 빌드 도구  | Gradle (Groovy DSL)           |                                                      |
| API 예외 처리  | `@ControllerAdvice` 전역 처리     |                                                      |
| API 문서화    | Springdoc OpenAPI(Swagger UI) |                                                      |
| 프론트 빌드 도구  | Vite                          |                                                      |
| 프론트 언어     | TypeScript                    | API 응답 필드 타입 안정성 확보                                  |
| 스타일링       | styled-components             | 점수 구간별 동적 색상 표현에 유리                                  |
| AI 챗봇       | Groq Chat Completions API (OpenAI 호환, Function/Tool Calling) | SGIS/상권정보처럼 공식 SDK 없이 자체 WebClient 클라이언트로 구현 - 하우스 스타일 일관성 유지. 최초 OpenAI로 확정했으나 무료 티어 필요로 2026-08 Groq로 전환(저트래픽 포트폴리오 데모에 적합, 무료 티어 요청/토큰 한도 있음) |


## 확정 API (2개, 그 외 API는 사용 금지)

프로젝트 검토 과정에서 서울 실시간 도시데이터, 지자체별 유동인구 API 등은 지역 제한/신뢰도 문제로 **의도적으로 제외**했음. 코드에 새 공공데이터 API를 추가하기 전에 반드시 스스로 재검토를 거칠 것.

(참고: 이 "2개" 제한은 **공공데이터 API** 대상이다. 챗봇에 쓰는 Groq API는 통계/상권 원자료를 다루지 않는 별개 카테고리라 이 제한과 무관하다 — 위 "확정 기술 스택" 표 참고.)

### 1. SGIS (통계지리정보서비스) — 통계청

- Base URL: [`https://sgisapi.kostat.go.kr/OpenAPI3`](https://sgisapi.kostat.go.kr/OpenAPI3)
- 인증: `auth/authentication.json`에 consumer_key/secret → accessToken 발급 (만료 있음, 재발급 로직 필요)
- 핵심 엔드포인트: `stats/population.json` (행정구역별 인구/가구 통계)
- 좌표 확보용: `boundary/hadmarea.geojson` (행정구역 경계 폴리곤 — centroid 계산해서 `REGION.latitude/longitude`에 저장, 1회성 시딩용. 지도 마커 표시를 위해 실시간 상권정보 호출 대신 이 방식으로 확정 — 3주차 검증 중 발견)
  - ⚠️ **응답 좌표는 WGS84가 아니라 투영좌표계(EPSG:5179, 중부원점)** — centroid는 직접 계산(Shoelace), 좌표계 변환만 proj4j로 EPSG:5179→WGS84 처리할 것. 변환 안 하면 Kakao Map에 마커가 잘못된 위치에 찍힘 (3주차 REST 컨트롤러 구현 중 추가로 발견·해결, 반드시 지킬 것)
- 역할: 잠재 고객 규모/가구 구조 산출, 지역 좌표 확보

### 2. 소상공인시장진흥공단 상가(상권)정보

- Base URL: [`https://apis.data.go.kr/B553077/api/open/sdsc2`](https://apis.data.go.kr/B553077/api/open/sdsc2)
- 인증: 공공데이터포털 발급 `serviceKey`
- 핵심 오퍼레이션: `storeListInDong` (행정구역 단위 업소 조회, `divId="adongCd"`+`key`)
- 역할: 동일 업종 경쟁 밀집도 산출
- 주의: 1회 응답 최대 1,000건 — 대도시 조회 시 페이징 필수

두 API는 행정표준코드(`adm_cd` ↔ `ctprvnCd/signguCd/adongCd`)로 연결. ⚠️ **근본 원인 확정(서울 전체 353개 실행 + 진단 완료)**: 상권정보의 `adongCd`는 SGIS의 `adm_cd`와 **완전히 독립적으로 관리되는 별개의 코드 체계**임(분동/코드 재편 이력이 서로 다름). "구 코드만 바꾸고 동 접미사는 SGIS 값을 복사"하는 변환은 원천적으로 성립하지 않으며, 지금까지 정상 동작한 140개는 접미사가 우연히 일치한 지역일 뿐. 실제 두 시스템 모두 데이터는 완전한데 코드 번호 체계만 다름(예: 가락1동 = SGIS `11240660` vs 상권정보 `11710631`). **추가로 REGION 테이블 자체에 이름 불일치로 시딩 단계에서 걸러진 동이 다수 있음**(송파구 기준 27개 중 13개 누락 확인) — "서울 전체 353개"라는 숫자 자체도 과소집계 상태. 해결은 25개 구 전체를 `divId=signguCd`로 페이징 조회해 실제 `(adongCd, adongNm)`을 뽑고 SGIS 동명과 문자열 매칭해 REGION을 교체하는 방법만 유효(행정안전부 등 제3의 표준코드 사용, 자동 변환 규칙 추정은 모두 기각됨). 부수 발견: `SeoulRegionDiscoveryService.checkMappingLightweight`가 NODATA_ERROR를 "매핑 성립"으로 잘못 간주해 틀린 코드를 걸러내지 못하는 버그 — 해결책과 무관하게 같이 수정 필요.

## 데이터 흐름 (5계층)

```
[외부 API] SGIS + 상권정보
      ↓
[배치] @Scheduled 배치·정규화 (월 1회)
      ↓
[저장] PostgreSQL — 원자료 + 점수 캐시
      ↓
[API] Spring Boot REST — 랭킹/지도/상세
      ↓
[프론트] React — 랭킹 리스트 ↔ 지도 ↔ 상세 패널

```

**실시간 API 호출 금지** — 항상 배치로 수집한 자체 DB만 서비스가 조회한다. 외부 API는 배치 계층에서만 호출.

## 확장성 설계 원칙 (구현 시 반드시 준수)

1. **데이터 수집기는 인터페이스로 분리**: `DataCollector` 인터페이스 → `SgisCollector`, `StoreZoneCollector` 구현체. 새 API 소스 추가 시 기존 코드 수정 금지, 구현체만 추가.
2. **지역/업종은 코드 테이블로 관리, 하드코딩 절대 금지**: "서울만"이라는 제약은 스키마가 아니라 조회 조건(WHERE)/설정값으로 처리. `Region`, `IndustryCategory` 테이블에는 항상 전국 코드를 적재.
3. **가중치는 외부 설정으로 분리**: 점수 계산식에 숫자 하드코딩 금지. `ScoreWeightConfig` 테이블 또는 `application.yml`로 분리.
4. **API는 버전 프리픽스 사용**: `/api/v1/...`
5. **Controller는 Entity를 직접 반환하지 않음**: 반드시 DTO로 변환해서 응답.
6. **프론트 지역 선택은 설정 기반**: 서울을 코드에 고정하지 말고, 백엔드가 내려주는 지역 목록 API를 그대로 렌더링.

## DB 스키마 (핵심 엔티티, 3주차 실제 마이그레이션 반영)

> ⚠️ 새 마이그레이션을 추가하기 전에 반드시 `DB_스키마_변경_관리_가이드.md`의 원칙(적용된 마이그레이션 수정 금지, nullable 우선, 체크리스트)을 먼저 확인할 것.

```
REGION(regionCode PK, regionName, level, latitude, longitude)  ← latitude/longitude는 V6 추가 예정(아직 미구현)
INDUSTRY_CATEGORY(industryCode PK, industryName, level)
POPULATION_STAT(regionCode FK, year, totalPopulation, density, totalFamily, avgFamilyMemberCount)  ← 뒤 2개 V2 추가, nullable
STORE_COUNT(regionCode FK, industryCode FK, storeCount, snapshotDate)
SCORE_WEIGHT_CONFIG(configId PK, weightKey, weightValue)
SCORE_CACHE(regionCode FK, industryCode FK, totalScore, populationScore, householdScore, densityScore)  ← householdScore는 V3 추가, NOT NULL

```

## 대시보드 화면 구성 (확정)

탐색 흐름: **업종 선택(필수) → 랭킹 리스트 + 지도(양방향 연동) → 상세 패널**

상세 패널은 항상 다음을 함께 표시:

- 종합 점수 (크게)
- 브레이크다운 ①인구 규모 ②가구 구조 ③경쟁 여유도(구 "경쟁 밀집도") (각 항목의 원자료 값도 함께 노출 — "왜 이 점수인가"를 사용자가 확인할 수 있어야 함)

## 창업 매력도의 정의 (2026-08 재검토·확정)

**"이 지역에서 이 업종이 인기 있는가"가 아니라, "수요는 충분히 크면서 그 수요 대비 경쟁이 상대적으로 여유 있는가"를 나타내는 점수다.** "핫플레이스"가 아니라 "수요는 있는데 아직 덜 뚫린 곳"을 찾아주는 게 목적.

`densityScore` **계산 방식 변경**: 기존엔 동일 업종 업소 개수를 그대로 정규화해 "업소가 많을수록 점수가 높아지는" 방향이었음(A방식). 2026-08 재검토로 **인구 대비 업소 밀도** 기반(B방식)으로 변경 — "경쟁이 적을수록(여유 있을수록) 점수가 높아짐". API 필드명(`densityScore`)은 유지, 화면 라벨만 "경쟁 여유도"로 표기.

⚠️ **B-1(min-max 정규화) → B-2(퍼센타일 랭크)로 재수정됨**: B-1 방식은 인구 19명짜리 극단 이상치(둔촌1동) 하나가 전체 스케일을 지배해, 실제로 중앙값보다 47배 과열된 지역도 "여유"로 잘못 표시되는 결함이 발견됨(`창업매력도_정의_재검토_기록.md` 10절). **최종 확정(B-2)**: `population >= 100`인 지역만 대상으로 `storeCountPerCapita`를 `PERCENT_RANK()`로 정규화(`attractivenessTier` 계산에 이미 쓰는 방식 재사용). `population < 100`은 해당 업종에 대해 densityScore/totalScore 결측(null) 처리 — 임의 추정 금지.

## 가중치 산출 방법 (v2 확정, 2026-08 재산출) — 수요/경쟁여유도 2축 하이브리드 AHP

> v1(최초 산출)은 "경쟁 밀집도가 단순 업소 개수 기반"이던 시절의 근거였음. 정의가 "경쟁 여유도"로 바뀌며 최상위 비교(수요 vs 공급)의 근거 자체가 무효화되어 재산출함. 전체 배경은 `창업매력도_정의_재검토_기록.md` 참고.

3개 지표(인구 규모/가구 구조/경쟁 여유도)를 2단계 계층으로 비교:

```
        [종합 점수]
        /         \
   [수요]        [경쟁여유도]
   /     \
[인구규모] [가구구조]

```

1. 1단계 쌍대비교: 수요 vs 경쟁여유도 중요도
2. 2단계 쌍대비교: 수요 내부에서 인구 규모 vs 가구 구조 중요도
3. 최종 가중치 = 수요 가중치 × 하위 비율(인구 규모/가구 구조), 경쟁여유도 가중치 그대로

### 확정 가중치 수치 (v2)


| 비교             | Saaty 값               | 근거                                                                                                                      |
| -------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| 수요 vs 경쟁여유도    | **1(동등)**             | 경쟁여유도가 이미 인구 대비로 계산돼 수요 정보를 내부에 포함하므로, 한쪽에 임의로 비중을 더 주려면 데이터 근거(매출 상관관계 등)가 필요한데 확보 불가 — 근거 없는 편향을 주지 않는 게 가장 방어 가능한 선택 |
| 인구 규모 vs 가구 구조 | 3(인구규모 우위, **변경 없음**) | 인구 규모는 시장 절대 크기(1차 지표), 가구 구조는 세부 보정(2차 지표) — 경쟁 지표 재정의와 무관                                                             |



| 지표                       | v1(폐기) | v2(확정)    |
| ------------------------ | ------ | --------- |
| 인구 규모(`populationScore`) | 0.50   | **0.375** |
| 가구 구조(`householdScore`)  | 0.17   | **0.125** |
| 경쟁 여유도(`densityScore`)   | 0.33   | **0.5**   |


**구현 시 반드시 지킬 것**:

1. `densityScore` 계산 로직을 "population≥100 지역 대상 `PERCENT_RANK()` 퍼센타일 정규화" 방식으로 수정 (population&lt;100 지역은 densityScore/totalScore null 처리, 임의 추정 금지)
2. 가중치 숫자를 코드에 하드코딩하지 말고 `ScoreWeightConfig`에 v1→v2로 교체 시딩
3. **가중치·계산 로직 변경 후** `SCORE_CACHE` **전체 재계산 필수** — 안 하면 화면에 낡은 v1 기준 점수가 계속 보임
4. 계산 과정은 API 명세서 5.2.2절, 재검토 전체 배경은 `창업매력도_정의_재검토_기록.md` 참고

## 확정 REST API 응답 계약 (3주차 실제 구현·curl 검증 완료)

**중요**: 이전엔 프론트가 nested 구조(`{ region: {...}, ... }`)로 가정하고 있었으나, 실제 구현·검증 결과 **모두 flat 구조**로 확정됨. 앞으로 이 계약을 그대로 유지할 것 — 임의로 nested 구조로 바꾸지 말 것.

- `GET /api/v1/industries` → `[{ industryCode, industryName }]` (level 필드 없음)
- `GET /api/v1/scores/ranking?industryCode=` → `[{ regionCode, regionName, totalScore, populationScore, householdScore, densityScore, latitude, longitude, percentileRank, attractivenessTier }]` (flat, `householdScore`는 NOT NULL, `latitude`/`longitude`는 V6 시딩 완료 지역만 값 있음). 데이터 없으면 빈 배열을 200으로 반환(에러 아님).
- `GET /api/v1/scores/detail?regionCode=&industryCode=` → `{ regionName, industryName, totalScore, populationStat: {...}, competitionStat: {...} }` (regionName/industryName은 최상위 flat). `populationStat`**/**`competitionStat`**은 객체 전체가 null일 수 있음** — 프론트는 반드시 옵셔널 체이닝으로 처리. `populationStat.householdCount`/`avgHouseholdSize`는 여전히 nullable(V2가 NOT NULL 승격 안 함).
- `GET /api/v1/scores/weights` → `[{ weightKey, weightValue, weightGroup }]` (공개, 인증 불필요) — `SCORE_WEIGHT_CONFIG` 전체를 그대로 반환. 상세 패널의 가중치 안내 문구가 이 엔드포인트를 쓴다. 값 변경은 아래 admin 인증이 걸린 `PUT /api/v1/admin/score-weights/{weightKey}`에서만 가능(2026-08, 이슈 #17).
- `POST /api/v1/auth/register` `{ email, password, displayName }` → `{ id, email, displayName }` (201, 이메일 중복 409, 검증 실패 400) / `POST /api/v1/auth/login` → 같은 형태 응답 + 세션 쿠키(JSESSIONID), 자격 증명 실패 401 / `POST /api/v1/auth/logout` → 204 / `GET /api/v1/auth/me` → `{ id, email, displayName }`(비로그인 401). 아래 "사용자 인증 & 즐겨찾기" 섹션 참고(2026-08, 이슈 #19).
- `GET /api/v1/favorites` → `[{ id, regionCode, regionName, industryCode, industryName, createdAt }]`(로그인 필요) / `POST /api/v1/favorites` `{ regionCode, industryCode }` → `FavoriteResponse`(201, **멱등** — 같은 조합이면 기존 항목 반환, 없는 코드 404) / `DELETE /api/v1/favorites/{favoriteId}` → 204(본인 소유 아니거나 없으면 404). 상태 변경 요청(POST/DELETE/logout)은 `X-XSRF-TOKEN` 헤더 필요(아래 참고).

## 사용자 인증 & 즐겨찾기 (2026-08 추가, 이슈 #19)

즐겨찾기(관심 지역×업종 저장) + 지역 비교 뷰를 위해 **일반 사용자 로그인**을 도입했다(그전엔 로그인 개념이 없었고 admin 공유 API Key만 존재). 방식은 **세션 + HttpOnly 쿠키(Spring Security)** — JWT/OAuth 대비 단일 서버 데모에 구현이 단순하고 토큰이 JS에 노출되지 않아 방어 가능하다고 판단(대안 비교는 노션 "기술 선택 근거" 21절).

- **보안 경계**: 공개 조회(랭킹/상세/업종/가중치/업소/챗봇)는 그대로 `permitAll`, `/api/v1/favorites/**`·`/api/v1/auth/me`만 인증 필요. `/api/v1/admin/**`은 **기존 `AdminApiKeyInterceptor`(공유 API Key)를 그대로 유지** — 세션 로그인과 admin 인증은 별개 체계다(Spring Security는 admin 경로를 `permitAll`로 통과시키고 MVC 인터셉터가 API Key를 강제).
- **비밀번호**: `BCryptPasswordEncoder` 해시로만 저장(평문 금지). 비밀번호 8~72자(BCrypt 72바이트 한계).
- **CSRF**: 세션 쿠키 기반이라 CSRF 방어 필요. `XSRF-TOKEN` 쿠키(HttpOnly=false)로 토큰을 내리고 프론트가 `X-XSRF-TOKEN` 헤더로 되돌려보낸다(더블 서밋). 상태 변경(즐겨찾기 추가/삭제, 로그아웃)에만 적용하고 admin(별도 키)·챗봇(공개)·로그인/회원가입(세션 부트스트랩)은 CSRF 제외.
- **CORS**: 세션 쿠키를 교차 오리진으로 주고받아야 해 `WebConfig`(MVC)가 아니라 `SecurityConfig`의 `CorsConfigurationSource`에서 `allowCredentials(true)`로 처리(허용 오리진 출처는 그대로 `spotscore.cors.allowed-origins`). 프론트 `client.ts`는 모든 요청에 `credentials: 'include'`.
- **스키마**: `app_user`(Flyway V13), `favorite`(V14, `(user_id, region_code, industry_code)` UNIQUE + FK `ON DELETE CASCADE`).
- **교차 사이트 쿠키 문제와 해결(prod)**: 배포 시 프론트(Cloudflare)와 백엔드(Render)가 다른 도메인이면 세션/CSRF 쿠키가 "교차 사이트(서드파티) 쿠키"가 되어 (1) 새로고침 후 세션 미유지(로그아웃) (2) 프론트 JS가 타 도메인 XSRF-TOKEN 쿠키를 못 읽어 즐겨찾기 POST가 CSRF 403으로 실패한다. Safari·최신 브라우저의 서드파티 쿠키 차단이면 `SameSite=None`으로도 해결 안 됨. **해결(확정)**: `frontend/functions/api/[[path]].ts`(Cloudflare Pages Function)가 `/api/*`를 Render(`BACKEND_URL` 환경변수)로 **리버스 프록시**한다. 프론트는 `VITE_API_BASE_URL`을 비워 상대경로 `/api/...`로 호출 → 브라우저는 프론트와 **동일 오리진**으로만 통신하고 백엔드 쿠키(Domain 속성 없음=host-only)가 프론트 도메인에 붙어 **1st-party**가 된다. `application-prod.yml`의 `server.servlet.session.cookie`(SameSite/Secure)와 `SecurityConfig`의 XSRF 쿠키 설정은 그대로 둬도 same-origin에서 정상 동작(dev는 둘 다 localhost=동일 사이트).

## Admin API 인증 (2026-08 추가)

`/api/v1/admin/**`(배치 트리거, 지역/업종 시딩, 점수 재계산, 가중치 수정)는 배포 후에도 인증 없이 열려 있던 게 발견돼(이슈 #15) `X-Admin-Api-Key` 헤더 인증을 추가했다. 헤더 값이 `spotscore.admin.api-key`(환경변수 `ADMIN_API_KEY`)와 일치해야 하며, 불일치·누락 시 401(`ErrorResponse` 포맷)을 반환한다. 키가 아예 설정 안 되어 있으면 dev는 모든 admin 요청을 차단(CORS 미설정 시 처리와 동일한 fail-closed 원칙), prod는 기본값 없이 필수라 미설정 시 부팅 자체가 실패한다(SGIS/상권정보/KOSIS 키와 동일한 컨벤션).

⚠️ **가중치 "조회"는 이 인증 대상이 아니다**: 프론트가 조회용으로 admin 엔드포인트에 편승해 쓰고 있던 게 발견돼(이슈 #17), 조회는 위 `GET /api/v1/scores/weights`로 공개 분리했다. `/api/v1/admin/score-weights`(GET)는 레거시로 남아있고 여전히 인증이 걸려 있다 — 새 코드는 공개 엔드포인트를 쓸 것.

## 점수 해석 기준 (확정) — 퍼센타일 밴드

종합 점수(min-max 정규화)는 데이터셋 크기가 바뀔 때마다 절대 숫자가 흔들림(실제로 매핑 이슈 해결 전/후 역삼1동 45.69→73.65로 변동 — 동네가 좋아진 게 아니라 비교 모집단이 커진 것). **고정된 절대 점수 기준("80점 이상") 대신 같은 업종 내 상대 순위(퍼센타일) 기반 등급을 사용한다.**


| 퍼센타일 구간(같은 업종 내) | 등급 라벨                 |
| ---------------- | --------------------- |
| 상위 10% 이내        | 매력적인 입지(`ATTRACTIVE`) |
| 상위 10~30%        | 괜찮은 입지(`GOOD`)        |
| 상위 30~70%        | 평균적인 입지(`AVERAGE`)    |
| 하위 30%           | 신중한 검토 필요(`CAUTION`)  |


**구간 경계(10%/30%/70%) 선정 근거**: 매출 데이터 등 통계적으로 도출한 값이 아니라 UX 관점의 합리적 기본값. 매출 데이터 확보 시(위 "보류된 대안") 실증적으로 재조정 가능 — 지금은 고정값으로 사용.

**구현 시 지킬 것**: `industryCode`로 파티션한 `PERCENT_RANK()` SQL 윈도우 함수로 조회 시점 계산(별도 저장 불필요, 내부 DB 쿼리라 외부 API 호출 금지 원칙과 무관). 브레이크다운(인구/가구/경쟁)은 그대로 유지 — 퍼센타일은 "요약", 브레이크다운은 "근거".

**보류된 대안**: 매출 데이터로 절대 기준선을 실증 도출하는 방법도 검토했으나, 전국 커버리지 매출 데이터(소상공인365)가 iframe 전용이라 원천 데이터 접근 불가 확인(2026-08). 향후 확장 아이디어로 보류.

## 연령 구성 지표(ageScore) 추가 — KOSIS 도입 확정 (v3, 2026-08)

**3번째 확정 공공데이터: KOSIS** "행정구역(읍면동)별/5세별 주민등록인구"(orgId 101, tblId `DT_1B04005N`). ⚠️ **코드 매핑 규칙 정정(2026-08, 실제 구현 시 라이브 검증으로 정정됨)**: `KOSIS = SGIS adm_cd + "00"`이 아니라 **`KOSIS = REGION.regionCode(상권정보 adongCd) + "00"`**이 맞는 규칙임(1168064000→역삼1동 정확히 확인). SGIS adm_cd로 변환하면 구가 다르게 매핑되는 오류가 생김 — 반드시 상권정보 코드 기준으로 구현할 것. 서울 426개 전수 검증 425/426(99.8%) 일치, 예외(용신동)는 기존 이슈 재확인.

⚠️ **통계 정의 차이 인지 필수**: SGIS=추계인구, KOSIS=주민등록인구로 서로 다른 통계(426개 중 57개 동 20% 이상 차이, 최대 65%). **해결(B안)**: 총인구/가구는 SGIS 유지(재검증 불필요), `ageScore`의 20~39세 비율은 **KOSIS 내부에서만 분자/분모 계산**(SGIS 총인구와 혼합 금지). 화면에 통계 기준 차이 툴팁 필수.

**업종별 방향(+/−/0, 데이터 근거 없는 개인 판단값)**: I2/R1/P1=+(청년층 유리), Q1=−(고령층 유리), 나머지 대분류=0(중립, 방향 안 매김). 방향 0인 업종은 기존 3개 지표만 사용 — 업종별로 지표 개수가 달라짐.

**구현 시 지킬 것**:

1. population&lt;100 임계값 재사용(densityScore와 동일 원칙, 임의 추정 금지)
2. AHP v3 확정(2026-08, B안 확정): 연령적합도를 사용하는 업종(POSITIVE/NEGATIVE)에서 핵심 3지표(인구/가구/경쟁여유도) vs 연령적합도를 3:1로 재산정(핵심=0.75, 연령=0.25). 3:1이라는 비율은 가중치 산출 방법(v2) 섹션에서 이미 쓰인 인구규모 vs 가구구조 관례(1차 핵심지표 대 2차 보정지표 = 3:1, Saaty값 3)를 재사용한 것 — 연령적합도가 일부 업종에만 적용되는 보정 지표라는 점에서 같은 논리. 계산 결과(핵심 3지표 내부 비율 3:1:4 유지): 인구규모=0.28125, 가구구조=0.09375, 경쟁여유도=0.375, 연령적합도=0.25 (POSITIVE/NEGATIVE 업종 기준). NEUTRAL 업종은 v2 수치(0.375/0.125/0.5) 그대로 사용.
3. 상세 배경은 API 명세서 5.2.5절 참고

## 업종 드롭다운 노출 기준 (확정) — 추천 30개

중분류 75개 전체를 드롭다운에 노출하면 고르기 어려워 추림. **기준: 실제 DB 업소 수(store_count) 상위 30개**(수동 큐레이션·대분류 단일화는 근거 부족/구분력 상실로 기각). 10개 대분류 전부가 상위 40개 안에 최소 1개씩 포함되어 균형 확인됨(단 I2 음식이 27.1%로 최대 비중 — 참고 사항). N=30은 대분류 균형과 드롭다운 길이의 절충점.

**구현 시 지킬 것**: `INDUSTRY_CATEGORY`에 `featured`(boolean) 컬럼으로 관리(업종 코드를 애플리케이션 코드에 나열하지 말 것 — 확장성 원칙 2번). `GET /api/v1/industries`는 기본 `featured=true`만 반환, `?all=true`로 전체 조회. 상세 기준·집계표는 API 명세서 5.2.4절 참고.

## 로깅 가이드

계층별로 아래 지점에서 로그를 남길 것. 레벨 기준: **ERROR**=즉시 대응 필요(인증 실패, DB 커넥션 실패), **WARN**=예상된 예외 케이스(N/A 값, 코드 매핑 실패 등 "아직 결정되지 않은 사항"과 연결된 케이스), **INFO**=정상 흐름 이정표, **DEBUG**=개발 중에만 활성화하는 상세 추적.


| 계층              | 로그 위치                     | 레벨         | 내용                                            |
| --------------- | ------------------------- | ---------- | --------------------------------------------- |
| 외부 API 호출       | AccessToken 발급            | INFO/ERROR | 발급 성공 시각 / 실패 응답 코드                           |
|                 | API 요청 시작                 | DEBUG      | 요청 URL, 파라미터(`adm_cd`, `divId` 등)             |
|                 | API 응답 수신                 | INFO       | HTTP 상태코드, 응답 건수                              |
|                 | API 응답 실패                 | ERROR      | `errCd`/`errMsg`(SGIS), `resultCode`(상권정보) 원문 |
|                 | 페이징 처리 중                  | DEBUG      | 현재 페이지 번호, 누적 수집 건수                           |
| 배치 스케줄러         | 배치 시작/종료                  | INFO       | 시작/종료 시각, 총 소요시간                              |
|                 | 수집 결과 요약                  | INFO       | 수집 건수 vs DB 저장 건수                             |
|                 | 배치 실패                     | ERROR      | 예외 스택트레이스, 실패 시점                              |
| 정규화/점수 계산       | 행정구역 코드 매핑                | WARN       | 매핑 실패한 `adm_cd`/`adongCd` 값                   |
|                 | N/A(비공개) 값 처리             | WARN       | 어떤 지역·필드가 N/A였는지                              |
|                 | 가중치 계산 결과                 | DEBUG      | 지역×업종 조합, 브레이크다운별 점수, 종합 점수                   |
|                 | 정규화 이상치                   | WARN       | min-max/z-score 계산 중 분모 0 등 예외                |
| DB 접근           | 쿼리/커넥션 실패                 | ERROR      | 실패한 쿼리, 커넥션 풀 상태                              |
|                 | 트랜잭션 롤백                   | WARN       | 롤백 사유                                         |
| API(Controller) | 요청 수신                     | INFO       | 엔드포인트, 요청 파라미터                                |
|                 | 응답 처리 시간                  | DEBUG      | 처리 소요시간(ms)                                   |
|                 | `@ControllerAdvice` 예외 처리 | ERROR      | 예외 종류, 발생 위치(서비스 메서드)                         |
| 프론트(React)      | TanStack Query `onError`  | error      | 실패한 API 엔드포인트, 응답 코드                          |
|                 | Kakao Map 렌더링 실패          | warn       | 지도 초기화 실패 사유                                  |
|                 | 상세 패널 데이터 누락              | warn       | 브레이크다운 필드 중 누락된 값                             |


## 폴더 구조

```
project/
├── backend/ (Spring Boot, Gradle)
│   └── src/main/java/.../
│       ├── collector/       # DataCollector 구현체 (Sgis, StoreZone)
│       ├── batch/           # @Scheduled 배치
│       ├── scoring/         # 가중치 계산 서비스
│       ├── domain/          # JPA Entity
│       ├── repository/      # Spring Data JPA Repository
│       ├── controller/      # REST 컨트롤러
│       └── dto/
└── frontend/ (React)
    └── components/
        ├── RankingList/
        ├── MapView/          # Kakao Map
        └── DetailPanel/       # Recharts 브레이크다운

```

## 아직 결정되지 않은 사항 (임의로 확정하지 말 것)

- ~~상권정보~~ `adongCd`~~가 SGIS~~ `adm_cd`~~와 독립된 코드 체계~~ — **해결 완료**: 25개 구 전체 signguCd 크로스워크 재구축으로 REGION 353→426개(73개 신규), 매핑 성공률 40%→98%(417/426). 기존 140개 회귀 없음.
- ~~**[후속]** `RegionCodeMappingValidator` **이름 대조 구분자 정규화 누락**: "종로1·2·3·4가동" 등 복합 동명 7건이 "·" vs "." 표기 차이로 실제 배치 시 여전히 불일치 처리됨.~~ — **해결 완료**: `DongNameNormalizer.normalizeLoose()`를 `RegionCodeMappingValidator`에도 적용해 양쪽 이름을 정규화한 뒤 대조하도록 반영함. 서울 426개 지역 전체 배치가 스킵 0건으로 성공한 것으로 재확인(2026-08-14).
- **[후속] 동대문구 용신동(**`sgisAdmCd: 11060810`**) 매핑 실패** — 크로스워크 스캔에서 대응 이름을 찾지 못함, 원인 조사 필요. (426개 지역 전체 배치 재실행 후에도 이 1곳만 여전히 실패 확인, 2026-08-14)
- ~~상권정보 1,000건 초과 시 페이징 처리 로직~~ — **해결 완료**: `StoreZoneCollector`가 `PAGE_SIZE=1000` 기준으로 `totalCount`를 넘을 때까지 재귀 페이징하도록 이미 구현되어 있음(실 배치 로그로 역삼1동 12페이지 이상 페이징 확인).
- SGIS 통계값 5 이하 비공개(N/A) 케이스 처리 방식(0 처리 vs 제외)
- 상권업종분류 vs 표준산업분류 불일치 시 업종 통일 기준
- ~~종합 점수 가중치~~ — **확정 완료** (인구규모 0.50 / 가구구조 0.17 / 경쟁밀집도 0.33) — 이 수치는 v1(폐기됨), 최종은 바로 위 "가중치 산출 방법(v2 확정)" 섹션의 0.375/0.125/0.5로 대신함. `SCORE_WEIGHT_CONFIG` 시딩도 완료 확인(`GET /api/v1/admin/score-weights` 실호출로 AGE_WEIGHT 0.25/CORE_WEIGHT 0.75/DEMAND_WEIGHT 0.5/HOUSEHOLD_RATIO 0.25/POPULATION_RATIO 0.75/SUPPLY_WEIGHT 0.5 전부 확인, 2026-08-14).
  - ⚠️ **stale 주석 주의(2026-08-22 확인)**: `V4__seed_ahp_hierarchy_weight_config.sql` 파일 내부 주석에는 "TODO(미확정): 아래 값은 팀 쌍대비교 진행 전 임시값(5:5/5:5)"이라고 적혀 있으나, 이는 V4 적용 시점 기준 설명이고 이후 `V11__add_directional_age_weight_and_fix_ahp_ratio.sql`이 `DEMAND_POPULATION_RATIO`/`DEMAND_HOUSEHOLD_RATIO`(0.5/0.5)를 확정치인 `POPULATION_RATIO`/`HOUSEHOLD_RATIO`(0.75/0.25, Saaty 3)로 실제로 교체하며 이미 지나간 이력이 됐다. 적용된 마이그레이션은 사후 수정하지 않는 원칙(`DB_스키마_변경_관리_가이드.md`)상 V4 주석은 그대로 두지만, **가중치 확정 여부는 반드시 최신 마이그레이션(V11) 또는 실제 DB/API 값 기준으로 판단할 것** — V4 주석만 보고 "아직 미확정"이라고 오판하지 말 것. (이 오판으로 이력서·포트폴리오에 "현재 값은 잠정치" 문구가 잘못 들어간 사고가 실제로 있었음, 2026-08-22 발견·수정.)
- `populationStat.householdCount`~~/~~`avgHouseholdSize` ~~nullable 여부~~ — **해소됨**: 서울 전체(426개) 재검증 결과 null 비율 0%

## 1개월 마일스톤


| 주차  | 목표                                                   |
| --- | ---------------------------------------------------- |
| 1주차 | Spring Initializr 셋업, API 인증키 발급, SGIS/상권정보 클라이언트 구현 |
| 2주차 | 배치로 원자료 DB 적재, 행정구역 코드 매핑 검증, 정규화·가중치 로직 구현          |
| 3주차 | REST 컨트롤러 구현, 프론트 기본 골격(랭킹+지도) 연동                    |
| 4주차 | 상세 패널(브레이크다운) 완성, 서울 UI 범위로 데모 마감                    |


## 커밋/코딩 시 유의사항

- 새로운 공공데이터 API를 추가하기 전에는 반드시 사용자에게 먼저 확인할 것 (확정 API 목록은 위 2개로 고정됨)
- 서울 외 지역명, 특정 업종 코드 등을 컨트롤러/서비스 로직에 하드코딩하지 말 것 — 항상 DB/설정에서 조회
- 가중치 숫자를 매직 넘버로 코드에 넣지 말 것

