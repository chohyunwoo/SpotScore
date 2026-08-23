import { useQueries } from '@tanstack/react-query';
import styled from 'styled-components';
import { fetchJson } from '../../api/client';
import { useFavorites, useRemoveFavorite } from '../../api/favorites';
import type { Favorite, ScoreDetail } from '../../types/domain';

interface CompareViewProps {
  onClose: () => void;
}

/**
 * 비교 표의 지표 정의. score 계열은 0~100 점수(높을수록 유리, 행 내 최댓값 강조),
 * raw 계열은 "왜 이 점수인가"를 뒷받침하는 원자료 값이다(CLAUDE.md 상세 패널 원칙).
 * 연령 적합도(ageScore)는 NEUTRAL 업종/데이터 없음이면 null이라 "—"로만 표시한다.
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

const TableScroll = styled.div`
  overflow: auto;
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
  min-width: 160px;
  padding: ${({ theme }) => theme.spacing.md};
  vertical-align: top;
  text-align: left;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  border-left: 1px solid ${({ theme }) => theme.colors.border};
`;

const ColumnHeadInner = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const ColumnTopRow = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const IndustryName = styled.span`
  font-size: ${({ theme }) => theme.typography.caption.size};
  color: ${({ theme }) => theme.colors.textSecondary};
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

const SectionDivider = styled.tr`
  td, th {
    padding-top: ${({ theme }) => theme.spacing.md};
  }
`;

const RawLabel = styled.td`
  padding: ${({ theme }) => `${theme.spacing.xs} ${theme.spacing.md}`};
  font-size: ${({ theme }) => theme.typography.label.size};
  font-weight: ${({ theme }) => theme.typography.label.weight};
  letter-spacing: ${({ theme }) => theme.typography.label.letterSpacing};
  color: ${({ theme }) => theme.colors.textTertiary};
  text-transform: uppercase;
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

function formatValue(value: number | null, metric: Metric): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  if (metric.kind === 'score') {
    return value.toFixed(1);
  }
  return `${value.toLocaleString()}${metric.unit ?? ''}`;
}

export function CompareView({ onClose }: CompareViewProps) {
  const { data: favorites } = useFavorites(true);
  const removeFavorite = useRemoveFavorite();
  const list: Favorite[] = favorites ?? [];

  // 즐겨찾기 각 조합의 상세 점수를 병렬로 조회한다. queryKey를 useScoreDetail과
  // 동일하게 맞춰(상세 패널을 이미 열어본 조합은) 캐시를 재사용한다.
  const detailQueries = useQueries({
    queries: list.map((fav) => ({
      queryKey: ['scores', 'detail', fav.regionCode, fav.industryCode],
      queryFn: () =>
        fetchJson<ScoreDetail>(
          `/api/v1/scores/detail?regionCode=${encodeURIComponent(fav.regionCode)}&industryCode=${encodeURIComponent(fav.industryCode)}`,
        ),
    })),
  });

  // 점수 행별 최댓값(동일 업종끼리가 아니라 선택한 후보들 사이 상대 비교) - 강조용.
  const bestByMetric = new Map<string, number>();
  METRICS.filter((m) => m.kind === 'score').forEach((metric) => {
    let best = -Infinity;
    detailQueries.forEach((q) => {
      const detail = q.data;
      if (!detail) return;
      const v = metric.get(detail);
      if (v !== null && v > best) best = v;
    });
    if (best !== -Infinity) bestByMetric.set(metric.key, best);
  });

  const scoreMetrics = METRICS.filter((m) => m.kind === 'score');
  const rawMetrics = METRICS.filter((m) => m.kind === 'raw');

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
          <TableScroll>
            <Table>
              <thead>
                <tr>
                  <MetricTh>지표</MetricTh>
                  {list.map((fav) => (
                    <ColumnTh key={fav.id}>
                      <ColumnHeadInner>
                        <IndustryName>{fav.industryName}</IndustryName>
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
                      </ColumnHeadInner>
                    </ColumnTh>
                  ))}
                </tr>
              </thead>
              <tbody>
                {scoreMetrics.map((metric) => (
                  <tr key={metric.key}>
                    <MetricRowTh>{metric.label}</MetricRowTh>
                    {list.map((fav, index) => {
                      const query = detailQueries[index];
                      if (query.isLoading) {
                        return <StateCell key={fav.id}>불러오는 중…</StateCell>;
                      }
                      if (query.isError || !query.data) {
                        return <StateCell key={fav.id}>데이터 없음</StateCell>;
                      }
                      const value = metric.get(query.data);
                      const best = bestByMetric.get(metric.key);
                      const isBest = value !== null && best !== undefined && value === best;
                      return (
                        <Cell key={fav.id} $best={isBest}>
                          {formatValue(value, metric)}
                        </Cell>
                      );
                    })}
                  </tr>
                ))}

                <SectionDivider>
                  <RawLabel>원자료</RawLabel>
                  {list.map((fav) => (
                    <RawLabel key={fav.id} />
                  ))}
                </SectionDivider>

                {rawMetrics.map((metric) => (
                  <tr key={metric.key}>
                    <MetricRowTh>{metric.label}</MetricRowTh>
                    {list.map((fav, index) => {
                      const query = detailQueries[index];
                      if (query.isLoading) {
                        return <StateCell key={fav.id}>불러오는 중…</StateCell>;
                      }
                      if (query.isError || !query.data) {
                        return <StateCell key={fav.id}>데이터 없음</StateCell>;
                      }
                      return (
                        <Cell key={fav.id} $best={false}>
                          {formatValue(metric.get(query.data), metric)}
                        </Cell>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </Table>
          </TableScroll>
        )}
      </Panel>
    </Backdrop>
  );
}
