import styled, { useTheme } from 'styled-components';
import { useRanking } from '../../api/scores';
import { useSelection } from '../../context/SelectionContext';
import { ATTRACTIVENESS_TIER_LABEL, getAttractivenessTierColor } from '../../styles/attractivenessTier';
import { getScoreColor } from '../../styles/scoreColor';
import type { RankingItem } from '../../types/domain';

const Panel = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radius};
  overflow: hidden;
  display: flex;
  flex-direction: column;
`;

const Header = styled.div`
  padding: 14px 16px;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  font-weight: 700;
`;

const List = styled.ol`
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  max-height: 480px;
`;

const Row = styled.li<{ $selected: boolean }>`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ $selected, theme }) => ($selected ? theme.colors.primaryLight : 'transparent')};

  &:hover {
    background: ${({ theme }) => theme.colors.primaryLight};
  }
`;

const Rank = styled.span`
  width: 24px;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-weight: 600;
`;

const RegionName = styled.span`
  flex: 1;
  font-weight: 500;
`;

const ScoreBadge = styled.span<{ $color: string }>`
  font-weight: 700;
  color: ${({ $color }) => $color};
`;

/** 리스트만 훑어봐도 대략적인 등급이 보이도록 하는 색상 점 - 등급 라벨은 title 툴팁으로. */
const TierDot = styled.span<{ $color: string }>`
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: ${({ $color }) => $color};
  flex-shrink: 0;
`;

const EmptyState = styled.div`
  padding: 32px 16px;
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
`;

export function RankingList() {
  const { industryCode, regionCode, setRegionCode } = useSelection();
  const { data: ranking, isLoading, isError } = useRanking(industryCode);
  const theme = useTheme();

  if (!industryCode) {
    return (
      <Panel>
        <Header>랭킹</Header>
        <EmptyState>업종을 먼저 선택해주세요.</EmptyState>
      </Panel>
    );
  }

  return (
    <Panel>
      <Header>랭킹</Header>
      {isLoading && <EmptyState>랭킹을 불러오는 중...</EmptyState>}
      {isError && <EmptyState>랭킹을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</EmptyState>}
      {ranking && ranking.length === 0 && (
        <EmptyState>아직 집계된 데이터가 없습니다. 배치 작업 완료 후 다시 확인해주세요.</EmptyState>
      )}
      {ranking && ranking.length > 0 && (
        <List>
          {ranking.map((item: RankingItem, index: number) => (
            <Row
              key={item.regionCode}
              $selected={item.regionCode === regionCode}
              onClick={() => setRegionCode(item.regionCode)}
            >
              <Rank>{index + 1}</Rank>
              <TierDot
                $color={getAttractivenessTierColor(item.attractivenessTier, theme)}
                title={ATTRACTIVENESS_TIER_LABEL[item.attractivenessTier]}
              />
              <RegionName>{item.regionName}</RegionName>
              <ScoreBadge $color={getScoreColor(item.totalScore, theme)}>{item.totalScore}</ScoreBadge>
            </Row>
          ))}
        </List>
      )}
    </Panel>
  );
}
