import { useQuery } from '@tanstack/react-query';
import { fetchJson } from './client';
import type { ScoreWeightConfig } from '../types/domain';

/**
 * GET /api/v1/admin/score-weights. 이름 그대로 관리자용 엔드포인트지만(인증 미적용
 * TODO 상태, ScoreWeightAdminController 참고), 가중치 값을 프론트가 하드코딩하지
 * 않고 노출할 수 있는 유일한 API라 수요/공급 카드의 가중치 안내 문구에 그대로 쓴다.
 * 가중치는 배치 재계산 전에는 잘 안 바뀌는 값이라 staleTime을 길게 둔다.
 */
export function useScoreWeights() {
  return useQuery({
    queryKey: ['admin', 'score-weights'],
    queryFn: () => fetchJson<ScoreWeightConfig[]>('/api/v1/admin/score-weights'),
    staleTime: 5 * 60 * 1000,
  });
}

/** weightKey로 값(0~1)을 찾아 백분율로 반환. 시딩 누락 등으로 못 찾으면 null. */
export function findWeightPercent(weights: ScoreWeightConfig[] | undefined, weightKey: string): number | null {
  const found = weights?.find((weight) => weight.weightKey === weightKey);
  return found ? Math.round(found.weightValue * 100) : null;
}
