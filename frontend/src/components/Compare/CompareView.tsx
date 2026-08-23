import { useQueries } from '@tanstack/react-query';
import styled from 'styled-components';
import { fetchJson } from '../../api/client';
import { useFavorites, useRemoveFavorite } from '../../api/favorites';
import type { Favorite, ScoreDetail } from '../../types/domain';

interface CompareViewProps {
  onClose: () => void;
}

/**
 * 비교 표의 지표 정의. score 계열은 0~100 점수(높을수록 유리), raw 계열은 "왜 이 점수인가"를
 * 뒷받침하는 원자료 값이다(CLAUDE.md 상세 패널 원칙). 연령 적합도(ageScore)는 NEUTRAL 업종/
 * 데이터 없음이면 null이라 "—"로만 표시한다.
 *
 * ⚠️ score 계열(totalScore/densityScore 등)은 industryCode로 파티션한 퍼센타일/상대 순위라
 * "같은 업종 안에서만" 비교 의미가 있다. 그래서 이 뷰는 업종별로 표를 나누고, 행별 최고값
 * 강조도 같은 업종 그룹 안에서만 계산한다(서로 다른 업종의 점수를 나란히 비교하지 않음).
 */
type Metric = {
  key: string;
  label: string;
  kind: 'score' | 'raw';
  unit?: string;
  get: (detail: ScoreDetail) => number | null;
};

const METRICS: Metric[] = [
  { key: 'totalScore', label: '종합 점수', kind: 'score', get: (d) => d.totalScore },
  { key: 'populationScore', label: '인구 규모', kind: 'score', get: (d) => d.populationScore },
  { key: 'householdScore', label: '가구 구조', kind: 'score', get: (d) => d.householdScore },
  { key: 'densityScore', label: '경쟁 여유도', kind: 'score', get: (d) => d.densityScore },
  { key: 'ageScore', label: '연령 적합도', kind: 'score', get: (d) => d.ageStat?.ageScore ?? null },
  { key: 'totalPopulation', label: '총인구', kind: 'raw', unit: '명', get: (d) => d.populationStat?.totalPopulation ?? null },
  { key: 'householdCount', label: '가구 수', kind: 'raw', unit: '가구', get: (d) => d.populationStat?.householdCount ?? null },
  { key: 'perCapita', label: '인구 1만명당 업소', kind: 'raw', unit: '개', get: (d) => d.competitionStat?.storeCountPerCapita ?? null },
  { key: 'storeCount', label: '동일 업종 업소 수', kind: 'raw', unit: '개', get: (d) => d.competitionStat?.storeCount ?? null },
];

const SCORE_METRICS = METRICS.filter((m) => m.kind === 'score');
const RAW_METRICS = METRICS.filter((m) => m.kind === 'raw');

const Backdrop = styled.div`
  position: fixed;
  inset: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.spacing.lg};
  background: rgba(16, 24, 40, 0.45);
`;

const Panel = styled.div`
  width: 100%;
  max-width: 1040px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  background: ${({ theme }) => theme.colors.surface};
  border-radius: ${({ theme }) => theme.radius.lg};
  box-shadow: ${({ theme }) => theme.shadow.panel};
  overflow: hidden;
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${({ theme }) => `${theme.spacing.lg} ${theme.spacing.lg}`};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
`;

const HeaderTitle = styled.h2`
  margin: 0;
  font-size: ${({ theme }) => theme.typography.h2.size};
  font-weight: ${({ theme }) => theme.typography.h2.weight};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const CloseButton = styled.button`
  border: none;
  background: none;
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
  color: ${({ theme }) => theme.colors.textTertiary};
`;

const Body = styled.div`
  overflow: auto;
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.lg} ${theme.spacing.lg}`};
`;

const Caption = styled.p`
  margin: ${({ theme }) => `${theme.spacing.md} 0 ${theme.spacing.xs}`};
  font-size: ${({ theme }) => theme.typography.caption.size};
  color: ${({ theme }) => theme.colors.textSecondary};
  line-height: ${({ theme }) => theme.typography.caption.lineHeight};
`;

const GroupSection = styled.section`
  margin-top: ${({ theme }) => theme.spacing.lg};
`;

const GroupTitle = styled.h3`
  margin: 0 0 ${({ theme }) => theme.spacing.sm};
  font-size: ${({ theme }) => theme.typography.h3.size};
  font-weight: ${({ theme }) => theme.typography.h3.weight};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const TableScroll = styled.div`
  overflow-x: auto;
`;

const Table = styled.table`
  border-collapse: collapse;
  width: 100%;
  min-width: max-content;
`;

const MetricTh = styled.th`
  position: sticky;
  left: 0;
  z-index: 2;
  background: ${({ theme }) => theme.colors.backgroundAlt};
  text-align: left;
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-weight: ${({ theme }) => theme.typography.weight.semibold};
  color: ${({ theme }) => theme.colors.textSecondary};
  white-space: nowrap;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
`;

const ColumnTh = styled.th`
  min-width: 150px;
  padding: ${({ theme }) => theme.spacing.md};
  vertical-align: top;
  text-align: left;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  border-left: 1px solid ${({ theme }) => theme.colors.border};
`;

const ColumnTopRow = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const RegionName = styled.span`
  font-size: ${({ theme }) => theme.typography.body.size};
  font-weight: ${({ theme }) => theme.typography.weight.bold};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const RemoveButton = styled.button`
  flex-shrink: 0;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: ${({ theme }) => theme.colors.accent};
`;

const MetricRowTh = styled.th`
  position: sticky;
  left: 0;
  z-index: 1;
  background: ${({ theme }) => theme.colors.surface};
  text-align: left;
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-weight: ${({ theme }) => theme.typography.weight.medium};
  color: ${({ theme }) => theme.colors.textSecondary};
  white-space: nowrap;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
`;

const Cell = styled.td<{ $best: boolean }>`
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  border-left: 1px solid ${({ theme }) => theme.colors.border};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-weight: ${({ $best, theme }) => ($best ? theme.typography.weight.bold : theme.typography.weight.medium)};
  color: ${({ $best, theme }) => ($best ? theme.colors.accent : theme.colors.textPrimary)};
  white-space: nowrap;
`;

const RawLabelRow = styled.tr`
  td {
    padding: ${({ theme }) => `${theme.spacing.md} ${theme.spacing.md} ${theme.spacing.xs}`};
    font-size: ${({ theme }) => theme.typography.label.size};
    font-weight: ${({ theme }) => theme.typography.label.weight};
    letter-spacing: ${({ theme }) => theme.typography.label.letterSpacing};
    color: ${({ theme }) => theme.colors.textTertiary};
    text-transform: uppercase;
  }
`;

const StateCell = styled.td`
  padding: ${({ theme }) => theme.spacing.md};
  border-left: 1px solid ${({ theme }) => theme.colors.border};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  color: ${({ theme }) => theme.colors.textSecondary};
  text-align: center;
`;

const EmptyState = styled.div`
  padding: ${({ theme }) => theme.spacing.xxl};
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
`;

type DetailQuery = {
  data?: ScoreDetail;
  isLoading: boolean;
  isError: boolean;
};

type IndustryGroup = {
  industryCode: string;
  industryName: string;
  favorites: Favorite[];
};

function formatValue(value: number | null, metric: Metric): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  if (metric.kind === 'score') {
    return value.toFixed(1);
  }
  return `${value.toLocaleString()}${metric.unit ?? ''}`;
}

/** 삽입 순서를 유지하며 즐겨찾기를 업종별로 묶는다. */
function groupByIndustry(favorites: Favorite[]): IndustryGroup[] {
  const map = new Map<string, IndustryGroup>();
  for (const fav of favorites) {
    let group = map.get(fav.industryCode);
    if (!group) {
      group = { industryCode: fav.industryCode, industryName: fav.industryName, favorites: [] };
      map.set(fav.industryCode, group);
    }
    group.favorites.push(fav);
  }
  return [...map.values()];
}

export function CompareView({ onClose }: CompareViewProps) {
  const { data: favorites } = useFavorites(true);
  const removeFavorite = useRemoveFavorite();
  const list: Favorite[] = favorites ?? [];

  // 즐겨찾기 각 조합의 상세 점수를 병렬 조회. queryKey를 useScoreDetail과 동일하게 맞춰
  // (상세 패널을 이미 열어본 조합은) 캐시를 재사용한다.
  const detailQueries = useQueries({
    queries: list.map((fav) => ({
      queryKey: ['scores', 'detail', fav.regionCode, fav.industryCode],
      queryFn: () =>
        fetchJson<ScoreDetail>(
          `/api/v1/scores/detail?regionCode=${encodeURIComponent(fav.regionCode)}&industryCode=${encodeURIComponent(fav.industryCode)}`,
        ),
    })),
  });

  // 즐겨찾기 id → 상세 조회 결과. useQueries 결과는 list와 같은 순서라 인덱스로 매핑.
  const queryByFavoriteId = new Map<number, DetailQuery>();
  list.forEach((fav, index) => {
    const query = detailQueries[index];
    if (query) {
      queryByFavoriteId.set(fav.id, query);
    }
  });

  const groups = groupByIndustry(list);

  /** 같은 업종 그룹 안에서 해당 지표의 최댓값(강조용). 값이 하나도 없으면 null. */
  function bestForGroup(group: IndustryGroup, metric: Metric): number | null {
    let best = -Infinity;
    for (const fav of group.favorites) {
      const detail = queryByFavoriteId.get(fav.id)?.data;
      if (!detail) continue;
      const value = metric.get(detail);
      if (value !== null && value > best) best = value;
    }
    return best === -Infinity ? null : best;
  }

  function renderCells(group: IndustryGroup, metric: Metric) {
    const best = metric.kind === 'score' ? bestForGroup(group, metric) : null;
    return group.favorites.map((fav) => {
      const query = queryByFavoriteId.get(fav.id);
      if (!query || query.isLoading) {
        return <StateCell key={fav.id}>불러오는 중…</StateCell>;
      }
      if (query.isError || !query.data) {
        return <StateCell key={fav.id}>데이터 없음</StateCell>;
      }
      const value = metric.get(query.data);
      const isBest = value !== null && best !== null && value === best && group.favorites.length > 1;
      return (
        <Cell key={fav.id} $best={isBest}>
          {formatValue(value, metric)}
        </Cell>
      );
    });
  }

  return (
    <Backdrop onClick={onClose}>
      <Panel onClick={(event) => event.stopPropagation()}>
        <Header>
          <HeaderTitle>관심 지역 비교</HeaderTitle>
          <CloseButton type="button" onClick={onClose} aria-label="닫기">
            ×
          </CloseButton>
        </Header>

        {list.length === 0 ? (
          <EmptyState>
            아직 저장한 관심 지역이 없습니다.
            <br />
            랭킹이나 상세 패널에서 별표(☆)를 눌러 관심 지역×업종을 저장하세요.
          </EmptyState>
        ) : (
          <Body>
            <Caption>
              점수는 같은 업종 안에서의 상대 순위(퍼센타일)라 업종이 다르면 직접 비교되지 않아, 업종별로 나눠
              보여줍니다. 각 표에서 <strong>진하게 강조된 값</strong>이 그 업종 내 최고값이에요.
            </Caption>

            {groups.map((group) => (
              <GroupSection key={group.industryCode}>
                <GroupTitle>{group.industryName}</GroupTitle>
                <TableScroll>
                  <Table>
                    <thead>
                      <tr>
                        <MetricTh>지표</MetricTh>
                        {group.favorites.map((fav) => (
                          <ColumnTh key={fav.id}>
                            <ColumnTopRow>
                              <RegionName>{fav.regionName}</RegionName>
                              <RemoveButton
                                type="button"
                                title="비교에서 제거(즐겨찾기 해제)"
                                aria-label="즐겨찾기 해제"
                                onClick={() => removeFavorite.mutate(fav.id)}
                              >
                                ★
                              </RemoveButton>
                            </ColumnTopRow>
                          </ColumnTh>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {SCORE_METRICS.map((metric) => (
                        <tr key={metric.key}>
                          <MetricRowTh>{metric.label}</MetricRowTh>
                          {renderCells(group, metric)}
                        </tr>
                      ))}

                      <RawLabelRow>
                        <td colSpan={group.favorites.length + 1}>원자료</td>
                      </RawLabelRow>

                      {RAW_METRICS.map((metric) => (
                        <tr key={metric.key}>
                          <MetricRowTh>{metric.label}</MetricRowTh>
                          {renderCells(group, metric)}
                        </tr>
                      ))}
                    </tbody>
                  </Table>
                </TableScroll>
              </GroupSection>
            ))}
          </Body>
        )}
      </Panel>
    </Backdrop>
  );
}
