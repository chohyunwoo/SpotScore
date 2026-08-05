import { useQuery } from '@tanstack/react-query';
import { fetchJson } from './client';
import type { IndustryCategory } from '../types/domain';

/**
 * GET /api/v1/industries. 기본(includeAll=false)은 추천 업종(featured=true)만,
 * includeAll=true면 ?all=true로 전체를 받는다 - "추천 30개"라는 사실 자체를
 * 프론트에 하드코딩하지 않고 항상 백엔드 응답 기준으로 따른다(CLAUDE.md
 * 확장성 원칙 6.4절). includeAll별로 캐시 키를 분리해 두 목록을 동시에 들고
 * 있을 수 있게 한다(전체 보기에서 추천/전체 섹션을 나누려면 둘 다 필요).
 */
export function useIndustries(includeAll = false, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['industries', includeAll],
    queryFn: () => fetchJson<IndustryCategory[]>(`/api/v1/industries${includeAll ? '?all=true' : ''}`),
    enabled: options?.enabled ?? true,
  });
}
