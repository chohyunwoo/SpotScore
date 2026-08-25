/**
 * 디자인 시스템 토큰. "미니멀 신뢰형 톤"(토스 기반, 이 프로젝트 전용 팔레트)을
 * 한 곳에서 관리 — 컴포넌트에 색상/타이포/스페이싱을 하드코딩하지 않는다.
 *
 * 강조색은 accent(#1B4B4A, 인디고-틸 계열) 하나만 지도 마커·CTA·점수 배지에 쓴다.
 * scoreScale은 그 accent와 동일한 hue/saturation(HSL 179°/47%)에서 밝기만 다르게
 * 뽑은 5단계 그라데이션 — "여러 색을 섞은 신호등식 배색" 대신 단일 색상 스케일로
 * 점수를 표현하기 위함(사용자 승인 완료, 2026-08 팔레트 확정).
 *
 * 참고: 백엔드 AttractivenessTier enum은 4단계(퍼센타일 10/30/70% 기준)만 존재한다.
 * scoreScale의 5단계는 그 4단계 등급 배지를 대체하는 게 아니라, 지도 위 연속적인
 * totalScore를 색으로 칠할 때 쓰는 별도 용도다 — attractivenessTier.ts에서 4단계
 * 배지 색은 scoreScale 중 4개를 골라 쓴다.
 */
export const theme = {
  colors: {
    background: '#FFFFFF',
    backgroundAlt: '#F7F8FA',
    surface: '#FFFFFF',
    border: '#E5E7EB',

    textPrimary: '#1A1D29',
    textSecondary: '#6B7280',
    textTertiary: '#9CA3AF',

    accent: '#1B4B4A',
    accentHover: '#143838',
    accentSurface: '#EAF6F6',
    onAccent: '#FFFFFF',

    /** 점수 표현과 무관한 시스템 에러 상태 전용(붉은 계열은 여기에만 씀). */
    danger: '#E5484D',

    /**
     * 0~100 totalScore를 5개 구간으로 나눠 색으로 표현할 때 쓰는 단일 색상
     * 그라데이션(낮음→높음). 인덱스 0이 가장 낮은 구간, 4가 가장 높은 구간이며
     * 마지막 값은 accent와 동일하다.
     */
    scoreScale: ['#E9F7F7', '#B4E4E3', '#69C9C7', '#338E8D', '#1B4B4A'] as readonly string[],
  },

  typography: {
    fontFamily:
      "'Pretendard', 'Apple SD Gothic Neo', 'Malgun Gothic', -apple-system, BlinkMacSystemFont, sans-serif",
    weight: {
      regular: 400,
      medium: 500,
      semibold: 600,
      bold: 700,
      extrabold: 800,
    },
    /** 화면당 유일하게 강조하는 핵심 숫자(종합 점수) 전용. */
    display: { size: '64px', weight: 800, lineHeight: 1.1 },
    h1: { size: '28px', weight: 700, lineHeight: 1.3 },
    h2: { size: '20px', weight: 700, lineHeight: 1.4 },
    h3: { size: '16px', weight: 600, lineHeight: 1.4 },
    body: { size: '15px', weight: 500, lineHeight: 1.6 },
    bodySmall: { size: '13px', weight: 500, lineHeight: 1.5 },
    caption: { size: '12px', weight: 400, lineHeight: 1.5 },
    label: { size: '11px', weight: 600, lineHeight: 1.4, letterSpacing: '0.02em' },
  },

  spacing: {
    xs: '4px',
    sm: '8px',
    md: '16px',
    lg: '24px',
    xl: '32px',
    xxl: '48px',
  },

  radius: {
    sm: '8px',
    md: '12px',
    lg: '20px',
    pill: '999px',
  },

  shadow: {
    card: '0 2px 8px rgba(16, 24, 40, 0.06)',
    panel: '0 8px 24px rgba(16, 24, 40, 0.12)',
  },

  breakpoint: {
    /** 태블릿 가로: 사이드바+지도 2단 → 상하 2단으로 전환하는 경계. */
    tablet: '1024px',
    /** 폰: 고정 높이 대시보드를 세로 스크롤형 레이아웃으로 전환하는 경계. */
    mobile: '600px',
  },
} as const;

export type AppTheme = typeof theme;

declare module 'styled-components' {
  export interface DefaultTheme extends AppTheme {}
}
