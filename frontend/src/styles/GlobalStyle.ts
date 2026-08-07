import { createGlobalStyle } from 'styled-components';

export const GlobalStyle = createGlobalStyle`
  * {
    box-sizing: border-box;
  }

  html, body, #root {
    height: 100%;
  }

  body {
    margin: 0;
    /* 지도 대시보드 카드(순백)가 도드라지도록 페이지 캔버스는 라이트 그레이로 둔다. */
    background: ${({ theme }) => theme.colors.backgroundAlt};
    color: ${({ theme }) => theme.colors.textPrimary};
    font-family: ${({ theme }) => theme.typography.fontFamily};
  }

  button, select {
    font-family: inherit;
  }
`;
