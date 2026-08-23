import styled from 'styled-components';
import { useAddFavorite, useFavorites, useRemoveFavorite } from '../../api/favorites';
import { useAuth } from '../../context/AuthContext';

interface FavoriteStarProps {
  regionCode: string;
  industryCode: string;
  /** 랭킹 행(작게)과 상세 패널(크게)에서 크기를 달리 쓴다. */
  size?: 'sm' | 'md';
  className?: string;
}

const StarButton = styled.button<{ $active: boolean; $size: 'sm' | 'md' }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: ${({ $size }) => ($size === 'md' ? '32px' : '24px')};
  height: ${({ $size }) => ($size === 'md' ? '32px' : '24px')};
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: ${({ $size }) => ($size === 'md' ? '20px' : '15px')};
  line-height: 1;
  color: ${({ $active, theme }) => ($active ? theme.colors.accent : theme.colors.textTertiary)};
  transition: color 0.15s ease, transform 0.1s ease;

  &:hover {
    color: ${({ theme }) => theme.colors.accent};
  }

  &:active {
    transform: scale(0.9);
  }

  &:disabled {
    cursor: default;
    opacity: 0.5;
  }
`;

/**
 * 관심 "지역x업종" 조합을 저장/해제하는 별표 토글. 랭킹 행·상세 패널 어디서든
 * regionCode/industryCode만 넘기면 동작한다. 비로그인 사용자가 누르면 저장 대신
 * 로그인 모달로 유도한다(즐겨찾기는 사용자별 서버 저장이므로 로그인이 전제).
 */
export function FavoriteStar({ regionCode, industryCode, size = 'sm', className }: FavoriteStarProps) {
  const { isAuthenticated, openAuthModal } = useAuth();
  const { data: favorites } = useFavorites(isAuthenticated);
  const addFavorite = useAddFavorite();
  const removeFavorite = useRemoveFavorite();

  const existing = favorites?.find(
    (fav) => fav.regionCode === regionCode && fav.industryCode === industryCode,
  );
  const isFavorite = Boolean(existing);
  const isPending = addFavorite.isPending || removeFavorite.isPending;

  const handleClick = (event: React.MouseEvent) => {
    // 랭킹 행은 별표를 감싼 바깥 영역이 지역 선택 버튼이라, 별표 클릭이 행 선택으로
    // 전파되지 않도록 막는다.
    event.stopPropagation();
    if (!isAuthenticated) {
      openAuthModal('login');
      return;
    }
    if (existing) {
      removeFavorite.mutate(existing.id);
    } else {
      addFavorite.mutate({ regionCode, industryCode });
    }
  };

  return (
    <StarButton
      type="button"
      className={className}
      $active={isFavorite}
      $size={size}
      onClick={handleClick}
      disabled={isPending}
      aria-pressed={isFavorite}
      aria-label={isFavorite ? '즐겨찾기 해제' : '즐겨찾기 추가'}
      title={
        !isAuthenticated
          ? '로그인하고 관심 지역으로 저장'
          : isFavorite
            ? '즐겨찾기 해제'
            : '관심 지역으로 저장'
      }
    >
      {isFavorite ? '★' : '☆'}
    </StarButton>
  );
}
