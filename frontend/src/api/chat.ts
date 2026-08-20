import { useMutation } from '@tanstack/react-query';
import { postJson } from './client';
import type { ChatRequest, ChatResponse } from '../types/domain';

/**
 * POST /api/v1/chat (ChatController 확인) — 이 앱의 첫 useMutation 사용처(기존 훅은
 * 전부 읽기 전용 useQuery). 대화 상태를 서버가 저장하지 않으므로 캐시 무효화도 필요 없음.
 */
export function useSendChatMessage() {
  return useMutation({
    mutationFn: (request: ChatRequest) => postJson<ChatResponse>('/api/v1/chat', request),
  });
}
