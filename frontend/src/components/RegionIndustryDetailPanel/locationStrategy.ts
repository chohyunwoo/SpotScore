import type { ScoreDetail } from '../../types/domain';

/**
 * 입지 "약점 → 대응 전략" 힌트 엔진(이슈 #37, 1단계). SpotScore가 이미 계산한 점수
 * 지표만 재활용해, 낮은 지표에 대한 일반적 대응 방향을 규칙 기반으로 제시한다.
 * 외부 데이터/API 없음. 지원사업 매칭(2단계)은 별개(향후 로드맵 참고).
 *
 * ⚠️ 여기 규칙은 매출 등 데이터로 검증한 게 아니라 도메인 상식에 기반한 개인 판단값이다
 * (ageScore 방향성·퍼센타일 밴드 경계와 같은 성격) - 화면에도 "참고용 힌트"임을 명시한다.
 */

/**
 * 약한 지표 판정 임계값(0~100). 통계로 도출한 값이 아니라 "눈에 띄게 낮다"를 가르는
 * UX용 합리적 기본값 - 퍼센타일 밴드 경계(10/30/70%)와 같은 성격의 고정값이다.
 */
export const WEAK_SCORE_THRESHOLD = 40;

/**
 * 강한 지표 판정 임계값(0~100). 약점이 없을 때 "그럼 뭘 살려 공략하나"를 안내하기 위해,
 * 눈에 띄게 높은 지표를 가르는 기준. 40~60 구간은 강점도 약점도 아닌 "평균"으로 본다.
 */
export const STRONG_SCORE_THRESHOLD = 60;

export interface StrategyHint {
  key: string;
  /** 지표 이름(화면 표시용). */
  label: string;
  /** 0~100 지표 점수. */
  score: number;
  /** 해당 지표에 대한 대응/공략 전략 힌트. */
  hint: string;
}

type MetricKey = 'density' | 'population' | 'household' | 'age';

/**
 * 지표 라벨/힌트 규칙. 컴포넌트에 문자열을 흩지 않고 여기 한 곳에서만 관리한다
 * (CLAUDE.md 확장성 원칙 - 규칙/기준값을 코드 곳곳에 하드코딩하지 않는다).
 * 라벨은 약점·강점이 공유하고, 힌트만 방향에 따라 나뉜다.
 */
const METRIC_LABELS: Record<MetricKey, string> = {
  density: '경쟁 여유도',
  population: '인구 규모',
  household: '가구 구조',
  age: '연령 적합도',
};

/** 약한 지표 → 보완 전략 힌트. */
const WEAKNESS_HINTS: Record<MetricKey, string> = {
  density: '이미 경쟁이 치열한 편이에요. 차별화·브랜딩이나 스마트상점 같은 접근으로 경쟁 우위를 만드는 걸 검토해 보세요.',
  population: '배후 수요가 작은 편이에요. 배달·온라인 판로로 오프라인 수요의 한계를 보완하는 걸 검토해 보세요.',
  household: '가구 구성이 이 업종과 잘 안 맞을 수 있어요. 1인/다인 가구 비중에 맞춘 상품·포장 구성을 검토해 보세요.',
  age: '주 고객 연령대와 지역 인구 구성이 어긋나요. 연령대에 맞춘 메뉴·마케팅으로 타겟을 조정해 보세요.',
};

/** 강한 지표 → 그 강점을 살리는 공략 전략 힌트(약점이 없을 때 안내). */
const STRENGTH_HINTS: Record<MetricKey, string> = {
  density: '수요 대비 경쟁이 여유 있어요. 아직 덜 뚫린 시장이라 빠른 선점과 입지 가시성 확보로 초기 점유율을 가져가는 걸 노려보세요.',
  population: '배후 수요가 두터워요. 유동 동선·접근성이 좋은 자리에서 회전율을 끌어올려 큰 수요를 최대한 담아내는 게 유리해요.',
  household: '가구 구성이 이 업종과 잘 맞아요. 지역 주 가구 유형(1인/다인)에 맞춘 상품·용량 구성으로 재방문율을 높여보세요.',
  age: '주 고객 연령대와 지역 인구가 잘 맞아요. 그 연령대가 선호하는 메뉴·채널(예: SNS·배달)에 마케팅을 집중해 강점을 극대화하세요.',
};

/**
 * 상세 응답의 4개 지표 점수를 한 배열로 모은다. null 지표(경쟁 여유도 표본 부족,
 * NEUTRAL 업종/표본 부족인 연령 적합도)는 score=null로 두고 이후 필터에서 제외한다.
 */
function metricScores(detail: ScoreDetail): Array<{ key: MetricKey; score: number | null }> {
  return [
    { key: 'density', score: detail.densityScore },
    { key: 'population', score: detail.populationScore },
    { key: 'household', score: detail.householdScore },
    // NEUTRAL 업종(ageDirection === null)이거나 표본 부족(ageScore === null)이면 연령 지표 제외.
    { key: 'age', score: detail.ageDirection ? detail.ageStat.ageScore : null },
  ];
}

/**
 * "약한 지표"(임계값 미만)를 뽑아 보완 전략 힌트로 변환한다.
 * 약한 것부터(점수 오름차순) 정렬해 반환한다.
 */
export function extractStrategyHints(detail: ScoreDetail): StrategyHint[] {
  return metricScores(detail)
    .filter((c): c is { key: MetricKey; score: number } =>
      c.score !== null && c.score < WEAK_SCORE_THRESHOLD)
    .sort((a, b) => a.score - b.score)
    .map((c) => ({ key: c.key, label: METRIC_LABELS[c.key], score: c.score, hint: WEAKNESS_HINTS[c.key] }));
}

/**
 * "강한 지표"(임계값 이상)를 뽑아 공략 전략 힌트로 변환한다. 약점이 없을 때 "그럼
 * 무엇을 살려 공략하나"를 안내하는 용도라, 강한 것부터(점수 내림차순) 정렬해 반환한다.
 */
export function extractStrengthHints(detail: ScoreDetail): StrategyHint[] {
  return metricScores(detail)
    .filter((c): c is { key: MetricKey; score: number } =>
      c.score !== null && c.score >= STRONG_SCORE_THRESHOLD)
    .sort((a, b) => b.score - a.score)
    .map((c) => ({ key: c.key, label: METRIC_LABELS[c.key], score: c.score, hint: STRENGTH_HINTS[c.key] }));
}
