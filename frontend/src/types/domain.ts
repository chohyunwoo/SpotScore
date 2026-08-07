/**
 * 프론트 타입 정의. 2026-08-04 기준 백엔드가 실제로 기동 중인 상태에서 확정함 —
 * 근거는 두 가지: (1) 라이브 http://localhost:8080/api-docs + 직접 curl 응답,
 * (2) backend/src/main/java/com/spotscore/dto/*.java 소스(RankingItem, IndustryResponse,
 * ScoreDetailResponse) 원본 대조. 이전 세션의 "REGION/SCORE_CACHE 엔티티를 그대로
 * 반영한 중첩 구조" 가정은 실제와 달랐음 — 실제 응답은 전부 플랫(flat) 구조.
 * 자세한 차이는 이번 세션 리포트의 "1. 실제 응답과 타입 대조 결과" 참고.
 */

/** GET /api/v1/industries 응답 아이템 (IndustryResponse.java 확인) */
export interface IndustryCategory {
  industryCode: string;
  industryName: string;
  // 실제 응답엔 level 필드가 없음(DB엔 industry_category.level 컬럼이 있지만
  // IndustryResponse DTO가 의도적으로 제외 — CLAUDE.md 원칙 5: Controller가
  // Entity를 그대로 반환하지 않음). 이전 세션에 있던 level: IndustryLevel 필드는 제거.
}

/**
 * GET /api/v1/scores/ranking 응답의 attractivenessTier 값 (AttractivenessTier.java
 * enum 확인, 2026-08-05 curl 검증). 퍼센타일 밴드 경계(10/30/70%)는 백엔드
 * AttractivenessTier에서만 관리 — 프론트에는 라벨/색상 매핑만 둔다.
 */
export type AttractivenessTier = 'ATTRACTIVE' | 'GOOD' | 'AVERAGE' | 'CAUTION';

/**
 * GET /api/v1/scores/ranking?industryCode=... 응답 아이템 (RankingItem.java 확인).
 * ScoreCalculationService가 B-2(2026-08)부터 인구 100명 미만 지역×업종 조합은
 * densityScore/totalScore를 null로 저장하지만, ScoreCacheRepository.findRankingWithPercentile이
 * `total_score IS NOT NULL`로 그런 행을 랭킹/퍼센타일 모집단에서 애초에 제외한다
 * (DB에서 직접 확인: 둔촌1동(인구 19명)은 어떤 industryCode로도 랭킹에 나오지 않음) —
 * 그래서 이 응답에 실리는 totalScore/densityScore는 항상 non-null이 보장된다.
 */
export interface RankingItem {
  regionCode: string;
  regionName: string;
  totalScore: number;
  populationScore: number;
  /**
   * score_cache.household_score는 V3 마이그레이션에서 기존 행을 0으로 백필한 뒤
   * NOT NULL로 승격됨(ScoreCache.java 컬럼도 nullable=false) — row가 존재하는 한
   * 항상 옴. populationStat.householdCount/avgHouseholdSize와는 다름(아래 참고).
   */
  householdScore: number;
  densityScore: number;
  /**
   * V6 마이그레이션(region.latitude/longitude 추가)으로 생김. 컬럼 자체는
   * NOT NULL 제약이 없고(DOUBLE PRECISION, nullable), RankingItem.java 주석도
   * "좌표 시딩 전에는 null일 수 있다"고 명시함(RegionCoordinateSeedingService가
   * 1회성으로 채움) — 필드 키는 항상 오지만 값은 시딩 전엔 null일 수 있어
   * required + nullable로 반영.
   */
  latitude: number | null;
  longitude: number | null;
  /**
   * 같은 industryCode 안에서만 계산되는 0~100 값, 낮을수록 상위(PERCENT_RANK()
   * 기반, score_cache에는 저장 안 됨 — 조회 시점 계산이라 매 응답마다 옴, null 아님).
   */
  percentileRank: number;
  attractivenessTier: AttractivenessTier;
}

/**
 * ScoreDetailResponse.PopulationStatDetail 확인 결과 — population_stat.total_population/
 * density(V1)와 total_family/avg_family_member_count(V2)는 전부 지금도 NOT NULL 제약이
 * 없음(CLAUDE.md "아직 결정되지 않은 사항": SGIS 5 이하 비공개 N/A 처리 정책 미정).
 * 라이브 표본(역삼1동/역삼2동) 2건은 마침 전부 값이 있었지만, 스키마상 4개 필드
 * 모두 null 가능 — 표본이 우연히 채워져 있었을 뿐 보장은 아니므로 optional 유지.
 */
export interface PopulationStatDetail {
  year: number;
  totalPopulation: number | null;
  density: number | null;
  householdCount: number | null;
  avgHouseholdSize: number | null;
}

/**
 * ScoreDetailResponse.CompetitionStatDetail 확인 — store_count.store_count/snapshot_date는
 * NOT NULL. storeCountPerCapita(2026-08 추가, 인구 1만명당 동일 업종 업소 수)는 인구 데이터가
 * 없거나 0인 지역에서 null(populationStat이 null인 경우와 동일한 원칙) — nullable 유지.
 */
export interface CompetitionStatDetail {
  storeCount: number;
  snapshotDate: string;
  storeCountPerCapita: number | null;
}

/** GET /api/v1/scores/detail?regionCode=...&industryCode=... 응답 (ScoreDetailResponse.java 확인) */
export interface ScoreDetail {
  regionCode: string;
  regionName: string;
  industryCode: string;
  industryName: string;
  /**
   * densityScore와 함께 null일 수 있음 — 아래 densityScore 주석 참고. 랭킹과 달리
   * 이 엔드포인트(ScoreQueryService.getDetail)는 region+industry로 직접 조회해
   * total_score IS NOT NULL 필터가 없으므로, 인구 100명 미만 지역은 그대로 null이 온다.
   */
  totalScore: number | null;
  populationScore: number;
  householdScore: number;
  /**
   * ScoreCalculationService의 B-2 최소 인구 기준(MIN_POPULATION_FOR_DENSITY=100) 미만인
   * 지역은 밀도 이상치(예: 인구 19명)가 퍼센타일 전체를 왜곡하는 것을 막기 위해
   * densityScore를 계산하지 않고 null로 저장한다(그 값에 의존하는 totalScore도 함께 null).
   * "0점"과 구분되는 값이므로 DetailPanel은 반드시 별도 문구로 표시해야 한다.
   */
  densityScore: number | null;
  /**
   * ScoreQueryService.getDetail()가 해당 지역에 population_stat row 자체가 없으면
   * .orElse(null) → PopulationStatDetail.from(null)이 통째로 null을 반환한다.
   * 내부 필드가 아니라 populationStat 자체가 null일 수 있음 — DetailPanel에서
   * optional chaining으로 처리해야 함(이전 세션엔 이 케이스가 반영 안 돼 있었음).
   */
  populationStat: PopulationStatDetail | null;
  /** 위와 동일한 이유로 해당 지역×업종의 store_count row가 없으면 전체가 null. */
  competitionStat: CompetitionStatDetail | null;
  calculatedAt: string;
}

/**
 * GET /api/v1/admin/score-weights 응답 아이템 (ScoreWeightConfigResponse.java 확인).
 * weightKey는 SCORE_WEIGHT_CONFIG에 시딩된 키 그대로 온다 - 프론트에 값을
 * 하드코딩하지 않고 항상 이 응답에서 읽는다(CLAUDE.md "가중치 숫자를 매직
 * 넘버로 코드에 넣지 말 것"). ScoreWeightService가 실제 리프 가중치 계산에
 * 쓰는 키: DEMAND_WEIGHT, SUPPLY_WEIGHT, DEMAND_POPULATION_RATIO,
 * DEMAND_HOUSEHOLD_RATIO — 이 중 DEMAND_WEIGHT/SUPPLY_WEIGHT가 수요/공급
 * 카드 하단 가중치 안내 문구에 쓰인다.
 */
export interface ScoreWeightConfig {
  weightKey: string;
  weightValue: number;
}
