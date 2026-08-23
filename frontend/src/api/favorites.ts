import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteJson, fetchJson, postJson } from './client';
import type { Favorite } from '../types/domain';

export const FAVORITES_QUERY_KEY = ['favorites'] as const;

/**
 * 내 즐겨찾기 목록. 로그인 상태에서만 enabled로 호출한다(비로그인 시 401 반복 방지) —
 * AuthContext가 isAuthenticated를 넘겨준다.
 */
export function useFavorites(enabled: boolean) {
  return useQuery({
    queryKey: FAVORITES_QUERY_KEY,
    queryFn: () => fetchJson<Favorite[]>('/api/v1/favorites'),
    enabled,
  });
}

/** 즐겨찾기 추가 — 서버가 멱등 처리하므로 이미 있으면 기존 항목을 그대로 돌려준다. */
export function useAddFavorite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { regionCode: string; industryCode: string }) =>
      postJson<Favorite>('/api/v1/favorites', vars),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: FAVORITES_QUERY_KEY }),
  });
}

export function useRemoveFavorite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (favoriteId: number) => deleteJson<void>(`/api/v1/favorites/${favoriteId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: FAVORITES_QUERY_KEY }),
  });
}
