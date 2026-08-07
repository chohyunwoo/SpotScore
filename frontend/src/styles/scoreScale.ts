import type { AppTheme } from './theme';

const SCALE_STEP_SIZE = 20;

/**
 * 0~100 totalScore를 theme.colors.scoreScale(단일 색상 5단계 그라데이션) 중
 * 한 구간으로 매핑한다. "여러 색을 섞은 신호등식 배색" 대신 인디고-틸 하나의
 * 명도 스케일만 쓰기로 확정한 팔레트 규칙을 따른다(theme.ts 참고).
 *
 * null/undefined는 ScoreCalculationService의 B-2 최소 인구 기준(100명) 미만이라
 * 애초에 점수를 산출하지 않은 경우 — "0점"과 구분되는 별도 색(textTertiary)으로 표시한다.
 */
export function getScoreScaleColor(score: number | null | undefined, theme: AppTheme): string {
  if (score === null || score === undefined || Number.isNaN(score)) {
    return theme.colors.textTertiary;
  }
  const clamped = Math.min(100, Math.max(0, score));
  const index = Math.min(theme.colors.scoreScale.length - 1, Math.floor(clamped / SCALE_STEP_SIZE));
  return theme.colors.scoreScale[index] ?? theme.colors.accent;
}

/**
 * scoreScale 배경 위에 올릴 텍스트 색. 스케일 앞쪽 2단계(옅은 톤)는 밝은 배경이라
 * textPrimary가, 뒤쪽 3단계(짙은 톤)는 onAccent(흰색)가 대비를 만족한다.
 */
export function getScoreScaleTextColor(score: number | null | undefined, theme: AppTheme): string {
  if (score === null || score === undefined || Number.isNaN(score)) {
    return theme.colors.textPrimary;
  }
  const clamped = Math.min(100, Math.max(0, score));
  return clamped >= 40 ? theme.colors.onAccent : theme.colors.textPrimary;
}

/**
 * "0점"과 혼동되지 않도록 densityScore/totalScore가 null일 때 쓰는 전용 라벨
 * (ScoreCalculationService의 B-2 최소 인구 기준 미만 — 창업매력도_정의_재검토_기록.md 10절).
 */
export const INSUFFICIENT_SAMPLE_LABEL = '데이터 부족';
