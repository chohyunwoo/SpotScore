# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 참고하는 프로젝트 컨텍스트입니다.

## 프로젝트 개요

**공공데이터 기반 창업 입지 추천 대시보드** — 예비 창업자를 타겟으로, 지역별 업종 밀집도(경쟁 강도) + 인구 통계를 조합해 "창업 매력도 점수"를 산출하는 서비스.

- 형식: 스터디 팀 프로젝트 (약 1개월, 기획~구현)
- 핵심 원칙: **백엔드는 전국 대응으로 설계, 프론트 UI는 MVP 범위(서울)로 축소**
- 점수는 항상 **"지역 × 업종" 조합**에 대해서만 계산 가능 (지역만으로는 점수 산출 불가)

## 확정 기술 스택

| 영역 | 선택 | 비고 |
|---|---|---|
| 백엔드 | Spring Boot (Gradle) | Java 경험은 있으나 Python 경험 없음 → Python 스택 배제하고 Spring 선택 |
| 프론트엔드 | React | |
| DB | PostgreSQL | 관계형 구조(지역 × 업종 = 점수), 추후 PostGIS 확장 가능 |
| 외부 API 호출 | WebClient | 비동기 병렬 호출(SGIS + 상권정보) |
| 배치 스케줄링 | Spring `@Scheduled` | 월 1회 배치 |
| DB 접근(ORM) | Spring Data JPA | |
| 지도 시각화 | Kakao Map API | |
| 차트(브레이크다운) | Recharts | |
| 데이터 페칭 | TanStack Query | |
| 상태관리 | React Context API | |
| DB 실행 환경 | Docker Compose | 팀 전체 동일 DB 버전/설정 재현 |
| 스키마 마이그레이션 | Flyway | 스키마 변경 이력 추적, 확장성 원칙 준수 검증 |
| 환경 분리 | Spring Profile (dev/prod) | |
| 백엔드 빌드 도구 | Gradle (Groovy DSL) | |
| API 예외 처리 | `@ControllerAdvice` 전역 처리 | |
| API 문서화 | Springdoc OpenAPI(Swagger UI) | |
| 프론트 빌드 도구 | Vite | |
| 프론트 언어 | TypeScript | API 응답 필드 타입 안정성 확보 |
| 스타일링 | styled-components | 점수 구간별 동적 색상 표현에 유리 |

## 확정 API (2개, 그 외 API는 사용 금지)

프로젝트 검토 과정에서 서울 실시간 도시데이터, 지자체별 유동인구 API 등은 지역 제한/신뢰도 문제로 **의도적으로 제외**했음. 코드에 새 공공데이터 API를 추가하기 전에 반드시 팀 논의를 거칠 것.

### 1. SGIS (통계지리정보서비스) — 통계청
- Base URL: `https://sgisapi.kostat.go.kr/OpenAPI3`
- 인증: `auth/authentication.json`에 consumer_key/secret → accessToken 발급 (만료 있음, 재발급 로직 필요)
- 핵심 엔드포인트: `stats/population.json` (행정구역별 인구/가구 통계)
- 좌표 확보용: `boundary/hadmarea.geojson` (행정구역 경계 폴리곤 — centroid 계산해서 `REGION.latitude/longitude`에 저장, 1회성 시딩용. 지도 마커 표시를 위해 실시간 상권정보 호출 대신 이 방식으로 확정 — 3주차 검증 중 발견)
    - ⚠️ **응답 좌표는 WGS84가 아니라 투영좌표계(EPSG:5179, 중부원점)** — centroid는 직접 계산(Shoelace), 좌표계 변환만 proj4j로 EPSG:5179→WGS84 처리할 것. 변환 안 하면 Kakao Map에 마커가 잘못된 위치에 찍힘 (3주차 REST 컨트롤러 구현 중 추가로 발견·해결, 반드시 지킬 것)
- 역할: 잠재 고객 규모/가구 구조 산출, 지역 좌표 확보

### 2. 소상공인시장진흥공단 상가(상권)정보
- Base URL: `https://apis.data.go.kr/B553077/api/open/sdsc2`
- 인증: 공공데이터포털 발급 `serviceKey`
- 핵심 오퍼레이션: `storeListInDong` (행정구역 단위 업소 조회, `divId="adongCd"`+`key`)
- 역할: 동일 업종 경쟁 밀집도 산출
- 주의: 1회 응답 최대 1,000건 — 대도시 조회 시 페이징 필수

두 API는 행정표준코드(`adm_cd` ↔ `ctprvnCd/signguCd/adongCd`)로 연결. 자릿수/포맷은 아직 표본 검증 전 — 코드에서 가정하지 말고 실제 데이터로 검증할 것.

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
- 브레이크다운 ①인구 규모 ②가구 구조 ③경쟁 밀집도 (각 항목의 원자료 값도 함께 노출 — "왜 이 점수인가"를 사용자가 확인할 수 있어야 함)

## 가중치 산출 방법 (확정) — 수요/공급 2축 하이브리드 AHP

3개 지표(인구 규모/가구 구조/경쟁 밀집도)를 한 번에 비교하지 않고 2단계 계층으로 나눔:

```
        [종합 점수]
        /         \
   [수요]          [공급]
   /     \            |
[인구규모] [가구구조]  [경쟁밀집도]
```

1. 1단계 쌍대비교: 수요 vs 공급 중요도
2. 2단계 쌍대비교: 수요 내부에서 인구 규모 vs 가구 구조 중요도 (공급은 항목 1개뿐이라 비교 불필요)
3. 최종 가중치 = 수요 가중치 × 하위 비율 (인구 규모/가구 구조), 공급 가중치 그대로(경쟁 밀집도)
4. 실제 아는 지역 데이터로 점수를 뽑아 상식과 맞는지 검증 → 안 맞으면 비교값 재조정

### 확정 가중치 수치

| 비교 | Saaty 값 | 근거 |
|---|---|---|
| 수요 vs 공급 | 2 (수요 우위) | 수요가 창업 성패의 기초 지표지만, 인구 많은 곳은 경쟁도 몰리는 경향이 있어 공급에도 1/3 비중 유지 |
| 인구 규모 vs 가구 구조 | 3 (인구규모 우위) | 인구 규모는 시장 절대 크기(1차 지표), 가구 구조는 세부 보정(2차 지표) |

**최종 가중치**: 인구 규모(`populationScore`) **0.50** / 가구 구조(`householdScore`) **0.17** / 경쟁 밀집도(`densityScore`) **0.33**

**구현 시 반드시 지킬 것**: 이 값을 숫자로 코드에 하드코딩하지 말고 `ScoreWeightConfig` 테이블에 시딩할 것 (기존 임시 균등값 1/3씩 교체). 계산 과정은 API 명세서 5.2.2절 참고.

## 확정 REST API 응답 계약 (3주차 실제 구현·curl 검증 완료)

**중요**: 이전엔 프론트가 nested 구조(`{ region: {...}, ... }`)로 가정하고 있었으나, 실제 구현·검증 결과 **모두 flat 구조**로 확정됨. 앞으로 이 계약을 그대로 유지할 것 — 임의로 nested 구조로 바꾸지 말 것.

- `GET /api/v1/industries` → `[{ industryCode, industryName }]` (level 필드 없음)
- `GET /api/v1/scores/ranking?industryCode=` → `[{ regionCode, regionName, totalScore, populationScore, householdScore, densityScore, latitude, longitude }]` (flat, `householdScore`는 NOT NULL, `latitude`/`longitude`는 V6 시딩 완료 지역만 값 있음). 데이터 없으면 빈 배열을 200으로 반환(에러 아님).
- `GET /api/v1/scores/detail?regionCode=&industryCode=` → `{ regionName, industryName, totalScore, populationStat: {...}, competitionStat: {...} }` (regionName/industryName은 최상위 flat). **`populationStat`/`competitionStat`은 객체 전체가 null일 수 있음** — 프론트는 반드시 옵셔널 체이닝으로 처리. `populationStat.householdCount`/`avgHouseholdSize`는 여전히 nullable(V2가 NOT NULL 승격 안 함).

## 로깅 가이드

계층별로 아래 지점에서 로그를 남길 것. 레벨 기준: **ERROR**=즉시 대응 필요(인증 실패, DB 커넥션 실패), **WARN**=예상된 예외 케이스(N/A 값, 코드 매핑 실패 등 "아직 결정되지 않은 사항"과 연결된 케이스), **INFO**=정상 흐름 이정표, **DEBUG**=개발 중에만 활성화하는 상세 추적.

| 계층 | 로그 위치 | 레벨 | 내용 |
|---|---|---|---|
| 외부 API 호출 | AccessToken 발급 | INFO/ERROR | 발급 성공 시각 / 실패 응답 코드 |
| | API 요청 시작 | DEBUG | 요청 URL, 파라미터(`adm_cd`, `divId` 등) |
| | API 응답 수신 | INFO | HTTP 상태코드, 응답 건수 |
| | API 응답 실패 | ERROR | `errCd`/`errMsg`(SGIS), `resultCode`(상권정보) 원문 |
| | 페이징 처리 중 | DEBUG | 현재 페이지 번호, 누적 수집 건수 |
| 배치 스케줄러 | 배치 시작/종료 | INFO | 시작/종료 시각, 총 소요시간 |
| | 수집 결과 요약 | INFO | 수집 건수 vs DB 저장 건수 |
| | 배치 실패 | ERROR | 예외 스택트레이스, 실패 시점 |
| 정규화/점수 계산 | 행정구역 코드 매핑 | WARN | 매핑 실패한 `adm_cd`/`adongCd` 값 |
| | N/A(비공개) 값 처리 | WARN | 어떤 지역·필드가 N/A였는지 |
| | 가중치 계산 결과 | DEBUG | 지역×업종 조합, 브레이크다운별 점수, 종합 점수 |
| | 정규화 이상치 | WARN | min-max/z-score 계산 중 분모 0 등 예외 |
| DB 접근 | 쿼리/커넥션 실패 | ERROR | 실패한 쿼리, 커넥션 풀 상태 |
| | 트랜잭션 롤백 | WARN | 롤백 사유 |
| API(Controller) | 요청 수신 | INFO | 엔드포인트, 요청 파라미터 |
| | 응답 처리 시간 | DEBUG | 처리 소요시간(ms) |
| | `@ControllerAdvice` 예외 처리 | ERROR | 예외 종류, 발생 위치(서비스 메서드) |
| 프론트(React) | TanStack Query `onError` | error | 실패한 API 엔드포인트, 응답 코드 |
| | Kakao Map 렌더링 실패 | warn | 지도 초기화 실패 사유 |
| | 상세 패널 데이터 누락 | warn | 브레이크다운 필드 중 누락된 값 |

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

- 행정동 코드(SGIS `adm_cd` 7자리) ↔ 상권정보 `adongCd` 자릿수/포맷 표본 대조 필요
- 상권정보 1,000건 초과 시 페이징 처리 로직
- SGIS 통계값 5 이하 비공개(N/A) 케이스 처리 방식(0 처리 vs 제외)
- 상권업종분류 vs 표준산업분류 불일치 시 업종 통일 기준
- ~~종합 점수 가중치~~ — **확정 완료** (인구규모 0.50 / 가구구조 0.17 / 경쟁밀집도 0.33). `SCORE_WEIGHT_CONFIG` 시딩 아직 안 됐으면 진행 필요.
- **`populationStat.householdCount`/`avgHouseholdSize`가 null일 때 브레이크다운 화면 표시 방식** — 0 처리 vs "데이터 없음" 표시 중 미정 (3주차 curl 검증으로 nullable 확인됨)

## 1개월 마일스톤

| 주차 | 목표 |
|---|---|
| 1주차 | Spring Initializr 셋업, API 인증키 발급, SGIS/상권정보 클라이언트 구현 |
| 2주차 | 배치로 원자료 DB 적재, 행정구역 코드 매핑 검증, 정규화·가중치 로직 구현 |
| 3주차 | REST 컨트롤러 구현, 프론트 기본 골격(랭킹+지도) 연동 |
| 4주차 | 상세 패널(브레이크다운) 완성, 서울 UI 범위로 데모 마감 |

## 커밋/코딩 시 유의사항

- 새로운 공공데이터 API를 추가하기 전에는 반드시 사용자에게 먼저 확인할 것 (확정 API 목록은 위 2개로 고정됨)
- 서울 외 지역명, 특정 업종 코드 등을 컨트롤러/서비스 로직에 하드코딩하지 말 것 — 항상 DB/설정에서 조회
- 가중치 숫자를 매직 넘버로 코드에 넣지 말 것