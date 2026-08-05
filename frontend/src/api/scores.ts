import { useQuery } from '@tanstack/react-query';
import { fetchJson } from './client';
import type { RankingItem, ScoreDetail } from '../types/domain';

/** GET /api/v1/scores/ranking?industryCode=... (가정 — 백엔드 컨트롤러 미구현, Swagger 확정 전 확인 필요) */
export function useRanking(industryCode: string | null) {
  return useQuery({
    queryKey: ['scores', 'ranking', industryCode],
    queryFn: () =>
      fetchJson<RankingItem[]>(`/api/v1/scores/ranking?industryCode=${encodeURIComponent(industryCode as string)}`),
    enabled: industryCode !== null,
  });
}

/** GET /api/v1/scores/detail?regionCode=...&industryCode=... (가정 — 백엔드 컨트롤러 미구현, Swagger 확정 전 확인 필요) */
export function useScoreDetail(regionCode: string | null, industryCode: string | null) {
  return useQuery({
    queryKey: ['scores', 'detail', regionCode, industryCode],
    queryFn: () =>
      fetchJson<ScoreDetail>(
        `/api/v1/scores/detail?regionCode=${encodeURIComponent(regionCode as string)}&industryCode=${encodeURIComponent(industryCode as string)}`,
      ),
    enabled: regionCode !== null && industryCode !== null,
  });
}
