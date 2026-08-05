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
    background: ${({ theme }) => theme.colors.background};
    color: ${({ theme }) => theme.colors.textPrimary};
    font-family: 'Pretendard', 'Apple SD Gothic Neo', 'Malgun Gothic', -apple-system,
      BlinkMacSystemFont, sans-serif;
  }

  button, select {
    font-family: inherit;
  }
`;
