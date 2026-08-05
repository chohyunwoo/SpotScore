import { QueryCache, QueryClient } from '@tanstack/react-query';

/**
 * 로깅 가이드(CLAUDE.md) — 프론트(React) / TanStack Query onError:
 * "실패한 API 엔드포인트, 응답 코드"를 error 레벨로 남길 것.
 * TanStack Query v5는 useQuery 옵션에서 onError를 제거했으므로 QueryCache 전역 콜백에서 처리.
 */
export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error, query) => {
      console.error('[TanStack Query] request failed', {
        queryKey: query.queryKey,
        message: error instanceof Error ? error.message : error,
      });
    },
  }),
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 60_000,
    },
  },
});
