# SpotScore

공공데이터(SGIS 통계청 + 소상공인시장진흥공단 상가정보) 기반 창업 입지 추천 대시보드.
지역별 업종 경쟁 밀집도와 인구 통계를 조합해 **"지역 × 업종" 조합의 창업 매력도 점수**를 산출한다.

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
| 외부 API 호출 | WebClient (SGIS + 상권정보 비동기 병렬 호출) |
| 배치 | Spring `@Scheduled` (월 1회) |
| 지도 | Kakao Map JS SDK |
| 차트 | Recharts |
| 데이터 페칭 | TanStack Query |
| 스타일링 | styled-components |
| API 문서 | Springdoc OpenAPI (Swagger UI) |

## 데이터 흐름

```
[외부 API] SGIS + 상권정보
      ↓ (배치에서만 호출, 실시간 호출 금지)
[배치] @Scheduled 배치·정규화 (월 1회)
      ↓
[저장] PostgreSQL — 원자료 + 점수 캐시
      ↓
[API] Spring Boot REST — 랭킹/지도/상세
      ↓
[프론트] React — 랭킹 리스트 ↔ 지도 ↔ 상세 패널
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
    dto/
  src/main/resources/db/migration/   Flyway 마이그레이션

frontend/  React (Vite, TypeScript)
  src/
    api/                 REST 클라이언트 (TanStack Query)
    components/
      RankingList/
      MapView/            Kakao Map
      DetailPanel/        Recharts 브레이크다운
      IndustrySelector/
    context/
```

## 로컬 실행

### 1. 사전 준비

- JDK 21, Node 18+, Docker Desktop
- 외부 API 키 발급
  - SGIS(통계청 통계지리정보서비스): https://sgis.kostat.go.kr
  - 소상공인시장진흥공단 상가(상권)정보: https://www.data.go.kr
  - Kakao Map JavaScript 키: Kakao Developers > 내 애플리케이션

### 2. 환경 변수

```bash
cp backend/.env.example backend/.env    # SGIS/상권정보 키, DB 자격증명 입력
cp frontend/.env.example frontend/.env  # API base URL, Kakao Map 키 입력
```

### 3. DB 기동 + 백엔드 실행

```bash
cd backend
docker compose up -d          # PostgreSQL (Flyway가 기동 시 자동 마이그레이션)
./gradlew bootRun             # http://localhost:8080
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- dev 프로파일 기본 배치 대상: 강남구 역삼1동/역삼2동 (env `SPOTSCORE_BATCH_TARGET_REGIONS`로 확장 가능)

### 4. 배치 실행 + 좌표 시딩 (최초 1회)

```bash
curl -X POST http://localhost:8080/api/v1/admin/batch/run
curl -X POST http://localhost:8080/api/v1/admin/regions/seed-coordinates
```

### 5. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev                   # http://localhost:5173 (Kakao Map 키가 이 도메인으로 제한됨)
```

데모 시연 순서는 [frontend/DEMO.md](./frontend/DEMO.md) 참고.

## 주요 API

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/api/v1/industries` | 업종 목록 (기본 featured=true 30개, `?all=true`로 전체) |
| GET | `/api/v1/scores/ranking?industryCode=` | 업종별 지역 랭킹 (점수·퍼센타일·좌표) |
| GET | `/api/v1/scores/detail?regionCode=&industryCode=` | 지역×업종 상세 (종합 점수 + 브레이크다운) |
| POST | `/api/v1/admin/batch/run` | 배치 즉시 실행 |
| POST | `/api/v1/admin/regions/seed-coordinates` | 지역 좌표(위경도) 시딩 |
| POST | `/api/v1/admin/regions/discover-seoul` | 서울 행정동 크로스워크 탐색 |
| POST | `/api/v1/admin/regions/rebuild-crosswalk` | 행정구역 코드 크로스워크 재구축 |
| POST | `/api/v1/admin/scores/recalculate` | 점수 재계산 |
| GET | `/api/v1/admin/score-weights` | AHP 가중치 설정 조회 |
| POST | `/api/v1/admin/industries/seed-featured` | 업종 드롭다운 노출(featured) 목록 시딩 |

전체 요청/응답 스키마는 Swagger UI에서 확인.

## 확장성 설계 원칙

1. 데이터 수집기는 `DataCollector` 인터페이스로 분리 — 새 API 소스는 구현체만 추가
2. 지역/업종은 코드 테이블로 관리, 애플리케이션 코드에 하드코딩 금지 ("서울만"은 조회 조건일 뿐)
3. 점수 가중치는 `ScoreWeightConfig` 테이블/설정값으로 분리, 매직 넘버 금지
4. API는 `/api/v1/...` 버전 프리픽스 사용
5. Controller는 Entity를 직접 반환하지 않고 DTO로 변환

자세한 배경과 확정 사항은 [CLAUDE.md](./CLAUDE.md) 참고.
