import type { DefaultTheme } from 'styled-components';
import type { AttractivenessTier } from '../types/domain';

/**
 * percentileRank/attractivenessTier 표시용 매핑 (API 명세서 5.2.3절 퍼센타일 밴드).
 * 등급 경계값(10/30/70%) 자체는 백엔드 AttractivenessTier.java에서만 관리하고,
 * 여기는 이미 확정된 등급을 라벨/색으로만 바꾼다.
 */
export const ATTRACTIVENESS_TIER_LABEL: Record<AttractivenessTier, string> = {
  ATTRACTIVE: '매력적인 입지',
  GOOD: '괜찮은 입지',
  AVERAGE: '평균적인 입지',
  CAUTION: '신중한 검토 필요',
};

export const ATTRACTIVENESS_TIER_ICON: Record<AttractivenessTier, string> = {
  ATTRACTIVE: '🏆',
  GOOD: '✅',
  AVERAGE: '➖',
  CAUTION: '🔍',
};

/**
 * 여러 색을 섞은 신호등식 배색 대신, accent와 동일한 단일 색상 스케일
 * (theme.colors.scoreScale)에서 4단계만 골라 쓴다 — ATTRACTIVE가 가장 짙은
 * accent 그 자체, CAUTION은 위험 경고가 아니라 "재검토 권장" 톤이라 가장 옅은
 * 단계로 차분하게 표현한다.
 */
export function getAttractivenessTierColor(tier: AttractivenessTier, theme: DefaultTheme): string {
  const scale = theme.colors.scoreScale;
  switch (tier) {
    case 'ATTRACTIVE':
      return scale[4] ?? theme.colors.accent;
    case 'GOOD':
      return scale[3] ?? theme.colors.accent;
    case 'AVERAGE':
      return scale[2] ?? theme.colors.accent;
    case 'CAUTION':
      return theme.colors.textTertiary;
  }
}

export function formatPercentileLabel(percentileRank: number): string {
  const rounded = Math.round(percentileRank);
  return rounded <= 0 ? '상위 1% 이내' : `상위 ${rounded}%`;
}
