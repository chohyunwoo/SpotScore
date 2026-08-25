import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

// Vite React+TS 표준 flat config. 빌드 산출물(dist)과 Cloudflare Pages Functions
// (functions/는 별도 런타임 타입 환경이라 프론트 lint 대상에서 제외)은 무시한다.
export default tseslint.config(
  { ignores: ['dist', 'functions'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
      // styled-components 테마 타입 확장은 `interface DefaultTheme extends AppTheme {}`
      // 형태의 모듈 augmentation이 관용적이므로 단일 extends 빈 인터페이스는 허용한다
      // (빈 객체 타입 `{}` 자체는 계속 금지).
      '@typescript-eslint/no-empty-object-type': [
        'error',
        { allowInterfaces: 'with-single-extends' },
      ],
    },
  },
)
