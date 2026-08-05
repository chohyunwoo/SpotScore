import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { ThemeProvider } from 'styled-components';
import App from './App';
import { queryClient } from './api/queryClient';
import { SelectionProvider } from './context/SelectionContext';
import { GlobalStyle } from './styles/GlobalStyle';
import { theme } from './styles/theme';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <GlobalStyle />
        <SelectionProvider>
          <App />
        </SelectionProvider>
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>,
);
