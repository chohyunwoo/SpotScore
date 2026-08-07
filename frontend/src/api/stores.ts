import { useQuery } from '@tanstack/react-query';
import { fetchJson } from './client';
import type { StoreItem } from '../types/domain';

/**
 * GET /api/v1/stores?regionCode=&industryCode=. StoreController 문서에 명시된 대로
 * "상세 패널을 열었을 때만 호출하는 용도"라 regionCode/industryCode가 둘 다 있을 때만
 * enabled - 이게 정확히 상세 패널이 열려있는 조건과 같아서 별도 게이팅 로직이 필요
 * 없다(지역 선택 = 상세 패널 오픈). 데이터 없으면 빈 배열 200(에러 아님).
 */
export function useStores(regionCode: string | null, industryCode: string | null) {
  return useQuery({
    queryKey: ['stores', regionCode, industryCode],
    queryFn: () =>
      fetchJson<StoreItem[]>(
        `/api/v1/stores?regionCode=${encodeURIComponent(regionCode as string)}&industryCode=${encodeURIComponent(industryCode as string)}`,
      ),
    enabled: regionCode !== null && industryCode !== null,
  });
}
