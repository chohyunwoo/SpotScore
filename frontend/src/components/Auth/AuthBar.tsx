import styled from 'styled-components';
import { useFavorites } from '../../api/favorites';
import { useAuth } from '../../context/AuthContext';

interface AuthBarProps {
  onOpenCompare: () => void;
}

const Bar = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
`;

const Greeting = styled.span`
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  color: ${({ theme }) => theme.colors.textSecondary};
`;

const PrimaryButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.xs};
  padding: ${({ theme }) => `${theme.spacing.xs} ${theme.spacing.md}`};
  border: none;
  border-radius: ${({ theme }) => theme.radius.pill};
  background: ${({ theme }) => theme.colors.accent};
  color: ${({ theme }) => theme.colors.onAccent};
  font-family: inherit;
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-weight: ${({ theme }) => theme.typography.weight.semibold};
  cursor: pointer;

  &:hover {
    background: ${({ theme }) => theme.colors.accentHover};
  }

  &:disabled {
    opacity: 0.5;
    cursor: default;
  }
`;

const GhostButton = styled.button`
  padding: ${({ theme }) => `${theme.spacing.xs} ${theme.spacing.md}`};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radius.pill};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.textPrimary};
  font-family: inherit;
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-weight: ${({ theme }) => theme.typography.weight.medium};
  cursor: pointer;

  &:hover {
    border-color: ${({ theme }) => theme.colors.accent};
    color: ${({ theme }) => theme.colors.accent};
  }
`;

const CountBadge = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: ${({ theme }) => theme.radius.pill};
  background: ${({ theme }) => theme.colors.onAccent};
  color: ${({ theme }) => theme.colors.accent};
  font-size: 11px;
  font-weight: ${({ theme }) => theme.typography.weight.bold};
`;

/**
 * 화면 우상단 인증/즐겨찾기 진입 바. 로그인 상태면 인사말 + "관심 지역 비교"(개수
 * 배지) + 로그아웃을, 비로그인이면 로그인/회원가입 버튼을 보여준다.
 */
export function AuthBar({ onOpenCompare }: AuthBarProps) {
  const { user, isAuthenticated, isLoading, logout, openAuthModal } = useAuth();
  const { data: favorites } = useFavorites(isAuthenticated);

  if (isLoading) {
    return null;
  }

  if (!isAuthenticated) {
    return (
      <Bar>
        <GhostButton type="button" onClick={() => openAuthModal('login')}>
          로그인
        </GhostButton>
        <PrimaryButton type="button" onClick={() => openAuthModal('register')}>
          회원가입
        </PrimaryButton>
      </Bar>
    );
  }

  const count = favorites?.length ?? 0;

  return (
    <Bar>
      <Greeting>{user?.displayName}님</Greeting>
      <PrimaryButton type="button" onClick={onOpenCompare} disabled={count === 0} title={count === 0 ? '별표로 관심 지역을 먼저 저장하세요' : '관심 지역 비교'}>
        관심 지역 비교
        {count > 0 && <CountBadge>{count}</CountBadge>}
      </PrimaryButton>
      <GhostButton type="button" onClick={() => logout()}>
        로그아웃
      </GhostButton>
    </Bar>
  );
}
