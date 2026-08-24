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

/** GET /api/v1/stores/{bizesId}/place-link 응답 (StorePlaceLinkResponse.java 확인). */
interface StorePlaceLink {
  /** 카카오맵 장소 상세 URL. 키 미설정/검색 결과 없음이면 null → 프론트가 이름 검색으로 폴백. */
  placeUrl: string | null;
}

/**
 * 가게 상세 모달의 "카카오맵에서 보기"용 장소 URL 조회. Kakao Local 검색은 요청당 비용이
 * 있어(외부 API) 모달이 실제로 열렸을 때만(enabled) 호출한다. 결과는 잘 안 바뀌므로
 * 오래 캐시한다.
 */
export function useStorePlaceLink(bizesId: string | null) {
  return useQuery({
    queryKey: ['storePlaceLink', bizesId],
    queryFn: () =>
      fetchJson<StorePlaceLink>(`/api/v1/stores/${encodeURIComponent(bizesId as string)}/place-link`),
    enabled: bizesId !== null,
    staleTime: 60 * 60_000,
  });
}
