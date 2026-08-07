import type { AppTheme } from './theme';

/**
 * 점수 구간별 색상 매핑 (UI 표시용 임계값).
 * 주의: 이 임계값은 "가중치"가 아니라 화면 표시용 색상 구간일 뿐이며,
 * ScoreWeightConfig와 무관함. totalScore가 0~100 스케일이라고 가정한 placeholder —
 * 백엔드 정규화 방식(min-max/z-score)이 확정되면 실제 분포에 맞춰 재조정할 것.
 * 색상 값 자체는 theme.colors에서 가져와 테마와 어긋나지 않게 한다.
 */
export function getScoreColor(score: number | null | undefined, theme: AppTheme): string {
  if (score === null || score === undefined || Number.isNaN(score)) {
    return theme.colors.muted;
  }
  if (score >= 80) return theme.colors.success;
  if (score >= 60) return theme.colors.primary;
  if (score >= 40) return theme.colors.warning;
  return theme.colors.danger;
}
