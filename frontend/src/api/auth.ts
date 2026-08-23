import { ApiError, fetchJson, postJson } from './client';
import type { LoginPayload, RegisterPayload, User } from '../types/domain';

/** 로그인 세션의 사용자 정보. 비로그인(401)이면 null을 반환해 에러가 아니라 "로그아웃 상태"로 다룬다. */
export async function getMe(): Promise<User | null> {
  try {
    return await fetchJson<User>('/api/v1/auth/me');
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

export function login(payload: LoginPayload): Promise<User> {
  return postJson<User>('/api/v1/auth/login', payload);
}

export function register(payload: RegisterPayload): Promise<User> {
  return postJson<User>('/api/v1/auth/register', payload);
}

export function logout(): Promise<void> {
  // 로그아웃은 Spring Security logout 필터가 처리(204). CSRF 토큰 헤더는 postJson이 자동 첨부.
  return postJson<void>('/api/v1/auth/logout', {});
}
