import { useMemo, useState } from 'react';
import styled from 'styled-components';
import type { RankingItem } from '../../types/domain';

const MAX_RESULTS = 8;

const Wrapper = styled.div`
  position: absolute;
  top: ${({ theme }) => theme.spacing.md};
  left: ${({ theme }) => theme.spacing.md};
  z-index: 11;
  width: 240px;

  @media (max-width: ${({ theme }) => theme.breakpoint.tablet}) {
    width: calc(100% - ${({ theme }) => theme.spacing.md} * 2);
  }
`;

/**
 * 지도 배경(타일/오버레이) 위에 얹히는 플로팅 검색창이라, 카드처럼 옅은 그림자만
 * 주면 지도 배경색에 묻힐 수 있어 border까지 함께 준다(실측 없이도 안전하게
 * 대비를 확보하기 위한 선택 - 기존 카드 스타일보다 한 단계 더 확실하게).
 */
const SearchPill = styled.input`
  width: 100%;
  padding: ${({ theme }) => `10px ${theme.spacing.md}`};
  border-radius: ${({ theme }) => theme.radius.pill};
  border: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
  box-shadow: ${({ theme }) => theme.shadow.card};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-family: inherit;
  color: ${({ theme }) => theme.colors.textPrimary};

  &::placeholder {
    color: ${({ theme }) => theme.colors.textTertiary};
  }

  &:disabled {
    cursor: not-allowed;
    color: ${({ theme }) => theme.colors.textTertiary};
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.colors.accent};
    outline-offset: 1px;
  }
`;

const ResultDropdown = styled.div`
  margin-top: ${({ theme }) => theme.spacing.xs};
  border-radius: ${({ theme }) => theme.radius.md};
  border: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
  box-shadow: ${({ theme }) => theme.shadow.panel};
  max-height: 280px;
  overflow-y: auto;
`;

const ResultRow = styled.button`
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${({ theme }) => theme.spacing.sm};
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  border: none;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: ${({ theme }) => theme.colors.backgroundAlt};
  }
`;

const ResultRegionName = styled.span`
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-weight: ${({ theme }) => theme.typography.weight.medium};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const ResultScore = styled.span`
  font-size: ${({ theme }) => theme.typography.caption.size};
  font-weight: ${({ theme }) => theme.typography.weight.semibold};
  color: ${({ theme }) => theme.colors.textSecondary};
`;

const NoResultRow = styled.div`
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  color: ${({ theme }) => theme.colors.textSecondary};
`;

interface RegionSearchBoxProps {
  industryCode: string | null;
  ranking: RankingItem[] | undefined;
  onSelectRegion: (regionCode: string) => void;
}

/**
 * 지도 좌측 상단 플로팅 검색창. "지역만으로는 점수 산출 불가"(CLAUDE.md 핵심 원칙)
 * 때문에 전체 지역이 아니라 현재 선택된 업종의 랭킹 결과(ranking) 안에서만
 * regionName을 필터링한다 - 랭킹에 없는 지역은 애초에 이 업종의 점수 데이터가
 * 없다는 뜻이라 검색 결과에도 나오면 안 된다.
 */
export function RegionSearchBox({ industryCode, ranking, onSelectRegion }: RegionSearchBoxProps) {
  const [query, setQuery] = useState('');
  const [isFocused, setIsFocused] = useState(false);

  const trimmedQuery = query.trim();
  const results = useMemo(() => {
    if (!trimmedQuery) return [];
    return (ranking ?? []).filter((item) => item.regionName.includes(trimmedQuery)).slice(0, MAX_RESULTS);
  }, [ranking, trimmedQuery]);

  const showDropdown = isFocused && trimmedQuery.length > 0;

  const handleSelect = (regionCode: string) => {
    onSelectRegion(regionCode);
    setQuery('');
    setIsFocused(false);
  };

  return (
    <Wrapper>
      <SearchPill
        type="text"
        value={query}
        disabled={!industryCode}
        placeholder={industryCode ? '지역명으로 검색' : '업종을 먼저 선택해주세요'}
        onChange={(e) => setQuery(e.target.value)}
        onFocus={() => setIsFocused(true)}
        // 결과 항목 클릭보다 blur가 먼저 발생해 드롭다운이 닫혀버리는 걸 막기 위해
        // ResultRow는 onClick이 아니라 onMouseDown으로 선택을 처리한다(handleSelect
        // 참고) - 그래도 blur 자체는 그대로 두어 바깥을 클릭하면 닫히게 한다.
        onBlur={() => setIsFocused(false)}
        aria-label="지역 검색"
      />
      {showDropdown && (
        <ResultDropdown>
          {results.length > 0 ? (
            results.map((item) => (
              <ResultRow key={item.regionCode} type="button" onMouseDown={() => handleSelect(item.regionCode)}>
                <ResultRegionName>{item.regionName}</ResultRegionName>
                <ResultScore>{item.totalScore}</ResultScore>
              </ResultRow>
            ))
          ) : (
            <NoResultRow>검색 결과 없음</NoResultRow>
          )}
        </ResultDropdown>
      )}
    </Wrapper>
  );
}
