import { useQuery } from '@tanstack/react-query';
import { fetchJson } from './client';
import type { RankingItem, ScoreDetail } from '../types/domain';

/** GET /api/v1/scores/ranking?industryCode=... (ScoreController.getRanking 확인 — curl 검증 완료) */
export function useRanking(industryCode: string | null) {
  return useQuery({
    queryKey: ['scores', 'ranking', industryCode],
    queryFn: () =>
      fetchJson<RankingItem[]>(`/api/v1/scores/ranking?industryCode=${encodeURIComponent(industryCode as string)}`),
    enabled: industryCode !== null,
  });
}

/** GET /api/v1/scores/detail?regionCode=...&industryCode=... (ScoreController.getDetail 확인 — curl 검증 완료) */
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
