import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { getMe, login as loginRequest, logout as logoutRequest, register as registerRequest } from '../api/auth';
import { FAVORITES_QUERY_KEY } from '../api/favorites';
import type { LoginPayload, RegisterPayload, User } from '../types/domain';

const ME_QUERY_KEY = ['auth', 'me'] as const;

export type AuthModalMode = 'login' | 'register';

interface AuthContextValue {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (payload: LoginPayload) => Promise<User>;
  register: (payload: RegisterPayload) => Promise<User>;
  logout: () => Promise<void>;
  /** 로그인/회원가입 모달 상태. 비로그인 사용자가 즐겨찾기를 누르면 여기로 유도한다. */
  authModalMode: AuthModalMode | null;
  openAuthModal: (mode?: AuthModalMode) => void;
  closeAuthModal: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/**
 * 세션 기반 로그인 상태를 앱 전역에 공유한다(CLAUDE.md 상태관리 확정 스택: React
 * Context API). 사용자 정보는 GET /api/v1/auth/me를 TanStack Query로 단일 소스로
 * 두고, 로그인/회원가입 성공 시 그 캐시를 갱신한다.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [authModalMode, setAuthModalMode] = useState<AuthModalMode | null>(null);

  const meQuery = useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: getMe,
    // 비로그인은 null(정상 상태)이라 재시도할 이유가 없고, 세션은 자주 바뀌지 않으므로
    // staleTime을 길게 둔다.
    retry: false,
    staleTime: 5 * 60_000,
  });

  const user = meQuery.data ?? null;

  const applyAuthenticatedUser = useCallback(
    (loggedIn: User) => {
      queryClient.setQueryData(ME_QUERY_KEY, loggedIn);
      // 다른 계정으로 로그인했을 수 있으니 즐겨찾기 캐시를 무효화해 다시 불러온다.
      queryClient.invalidateQueries({ queryKey: FAVORITES_QUERY_KEY });
      setAuthModalMode(null);
    },
    [queryClient],
  );

  const loginMutation = useMutation({
    mutationFn: loginRequest,
    onSuccess: applyAuthenticatedUser,
  });

  const registerMutation = useMutation({
    mutationFn: registerRequest,
    onSuccess: applyAuthenticatedUser,
  });

  const logoutMutation = useMutation({
    mutationFn: logoutRequest,
    onSuccess: () => {
      queryClient.setQueryData(ME_QUERY_KEY, null);
      // 로그아웃 후 남의 세션에 이전 사용자의 즐겨찾기가 남지 않도록 캐시를 비운다.
      queryClient.removeQueries({ queryKey: FAVORITES_QUERY_KEY });
    },
  });

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isLoading: meQuery.isLoading,
      login: (payload) => loginMutation.mutateAsync(payload),
      register: (payload) => registerMutation.mutateAsync(payload),
      logout: () => logoutMutation.mutateAsync(),
      authModalMode,
      openAuthModal: (mode: AuthModalMode = 'login') => setAuthModalMode(mode),
      closeAuthModal: () => setAuthModalMode(null),
    }),
    [user, meQuery.isLoading, loginMutation, registerMutation, logoutMutation, authModalMode],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
