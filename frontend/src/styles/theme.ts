export const theme = {
  colors: {
    background: '#f7f8fa',
    surface: '#ffffff',
    border: '#e2e5eb',
    textPrimary: '#1a1d23',
    textSecondary: '#6b7280',
    primary: '#2f6feb',
    primaryLight: '#eaf1fe',
    success: '#1a7f37',
    warning: '#e8a317',
    danger: '#e5484d',
    muted: '#9ca3af',
  },
  radius: '10px',
  shadow: '0 1px 3px rgba(16, 24, 40, 0.08)',
} as const;

export type AppTheme = typeof theme;

declare module 'styled-components' {
  export interface DefaultTheme extends AppTheme {}
}
