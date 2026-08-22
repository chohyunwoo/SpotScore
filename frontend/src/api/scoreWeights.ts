import { useQuery } from '@tanstack/react-query';
import { fetchJson } from './client';
import type { ScoreWeightConfig } from '../types/domain';

/**
 * GET /api/v1/scores/weights — 가중치 값을 프론트가 하드코딩하지 않고 노출할 수 있는
 * 공개 읽기 전용 API(ScoreController 참고). 값 변경(PUT /api/v1/admin/score-weights/{key})은
 * 여전히 X-Admin-Api-Key로 보호되지만, 조회는 비밀 정보가 아니라 인증 없이 공개한다(이슈 #17).
 * 가중치는 배치 재계산 전에는 잘 안 바뀌는 값이라 staleTime을 길게 둔다.
 */
export function useScoreWeights() {
  return useQuery({
    queryKey: ['score-weights'],
    queryFn: () => fetchJson<ScoreWeightConfig[]>('/api/v1/scores/weights'),
    staleTime: 5 * 60 * 1000,
  });
}

/** weightKey로 값(0~1)을 찾아 백분율로 반환. 시딩 누락 등으로 못 찾으면 null. */
export function findWeightPercent(weights: ScoreWeightConfig[] | undefined, weightKey: string): number | null {
  const found = weights?.find((weight) => weight.weightKey === weightKey);
  return found ? Math.round(found.weightValue * 100) : null;
}
