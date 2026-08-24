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

export interface StrategyHint {
  key: string;
  /** 약한 지표 이름(화면 표시용). */
  label: string;
  /** 0~100 지표 점수. */
  score: number;
  /** 해당 약점에 대한 대응 전략 힌트. */
  hint: string;
}

interface StrategyRule {
  key: string;
  label: string;
  hint: string;
}

/**
 * 지표 → 대응 전략 힌트 규칙. 컴포넌트에 문자열을 흩지 않고 여기 한 곳에서만 관리한다
 * (CLAUDE.md 확장성 원칙 - 규칙/기준값을 코드 곳곳에 하드코딩하지 않는다).
 */
const STRATEGY_RULES: Record<'density' | 'population' | 'household' | 'age', StrategyRule> = {
  density: {
    key: 'density',
    label: '경쟁 여유도',
    hint: '이미 경쟁이 치열한 편이에요. 차별화·브랜딩이나 스마트상점 같은 접근으로 경쟁 우위를 만드는 걸 검토해 보세요.',
  },
  population: {
    key: 'population',
    label: '인구 규모',
    hint: '배후 수요가 작은 편이에요. 배달·온라인 판로로 오프라인 수요의 한계를 보완하는 걸 검토해 보세요.',
  },
  household: {
    key: 'household',
    label: '가구 구조',
    hint: '가구 구성이 이 업종과 잘 안 맞을 수 있어요. 1인/다인 가구 비중에 맞춘 상품·포장 구성을 검토해 보세요.',
  },
  age: {
    key: 'age',
    label: '연령 적합도',
    hint: '주 고객 연령대와 지역 인구 구성이 어긋나요. 연령대에 맞춘 메뉴·마케팅으로 타겟을 조정해 보세요.',
  },
};

/**
 * 상세 응답에서 "약한 지표"(임계값 미만)를 뽑아 대응 전략 힌트로 변환한다. null 지표
 * (경쟁 여유도 표본 부족, NEUTRAL 업종/표본 부족인 연령 적합도)는 판별에서 제외하고,
 * 약한 것부터(점수 오름차순) 정렬해 반환한다.
 */
export function extractStrategyHints(detail: ScoreDetail): StrategyHint[] {
  const candidates: Array<{ rule: StrategyRule; score: number | null }> = [
    { rule: STRATEGY_RULES.density, score: detail.densityScore },
    { rule: STRATEGY_RULES.population, score: detail.populationScore },
    { rule: STRATEGY_RULES.household, score: detail.householdScore },
    // NEUTRAL 업종(ageDirection === null)이거나 표본 부족(ageScore === null)이면 연령 지표 제외.
    { rule: STRATEGY_RULES.age, score: detail.ageDirection ? detail.ageStat.ageScore : null },
  ];

  return candidates
    .filter((c): c is { rule: StrategyRule; score: number } =>
      c.score !== null && c.score < WEAK_SCORE_THRESHOLD)
    .sort((a, b) => a.score - b.score)
    .map((c) => ({ key: c.rule.key, label: c.rule.label, score: c.score, hint: c.rule.hint }));
}
