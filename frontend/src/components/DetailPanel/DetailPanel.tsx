import { useEffect, useMemo } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import styled, { useTheme } from 'styled-components';
import { ApiError } from '../../api/client';
import { useRanking, useScoreDetail } from '../../api/scores';
import { useSelection } from '../../context/SelectionContext';
import {
  ATTRACTIVENESS_TIER_ICON,
  ATTRACTIVENESS_TIER_LABEL,
  formatPercentileLabel,
  getAttractivenessTierColor,
} from '../../styles/attractivenessTier';
import { getScoreColor } from '../../styles/scoreColor';

const Panel = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radius};
  padding: 20px;
`;

const EmptyState = styled.div`
  padding: 24px 0;
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
`;

const TitleRow = styled.div`
  margin-bottom: 12px;
`;

const RegionTitle = styled.h2`
  margin: 0 0 10px;
  font-size: 20px;
`;

/** "무엇"을 담당 - 왜 이 점수인지는 아래 브레이크다운 카드가 맡는다. */
const TierBadge = styled.div<{ $color: string }>`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1.5px solid ${({ $color }) => $color};
  color: ${({ $color }) => $color};
  font-weight: 700;
  font-size: 15px;
`;

/** 종합 점수는 등급 배지 아래 보조 정보로 유지 (숫자 자체보다 등급이 먼저 눈에 들어오도록). */
const TotalScoreSecondary = styled.div`
  margin-top: 8px;
  font-size: 13px;
  color: ${({ theme }) => theme.colors.textSecondary};
`;

const TotalScoreValue = styled.strong<{ $color: string }>`
  margin-left: 6px;
  font-size: 20px;
  font-weight: 800;
  color: ${({ $color }) => $color};
`;

const BreakdownGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 20px;
`;

const BreakdownCard = styled.div`
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radius};
  padding: 12px 14px;
`;

const BreakdownLabel = styled.div`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.textSecondary};
  margin-bottom: 4px;
`;

const BreakdownScore = styled.div<{ $color: string }>`
  font-size: 22px;
  font-weight: 700;
  color: ${({ $color }) => $color};
`;

const RawValue = styled.div`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.textSecondary};
  margin-top: 4px;
`;

const ChartWrapper = styled.div`
  height: 220px;
  margin-top: 24px;
`;

const NA = 'N/A';

export function DetailPanel() {
  const { industryCode, regionCode } = useSelection();
  const { data: detail, isLoading, isError, error } = useScoreDetail(regionCode, industryCode);
  const isNotFound = error instanceof ApiError && error.status === 404;
  const theme = useTheme();

  // ScoreDetailResponse엔 percentileRank/attractivenessTier가 없다(백엔드 커밋
  // 확인 - 5.4절 확정 계약에 없음). RankingList/MapView가 이미 같은 industryCode로
  // 랭킹을 불러와 있어 TanStack Query 캐시를 그대로 재사용하고(추가 호출 없음),
  // 그 안에서 선택된 regionCode 항목을 찾아 등급 배지에 쓴다.
  const { data: ranking } = useRanking(industryCode);
  const rankingItem = useMemo(
    () => ranking?.find((item) => item.regionCode === regionCode) ?? null,
    [ranking, regionCode],
  );

  const missingFields = useMemo(() => {
    if (!detail) return [];
    const missing: string[] = [];
    // SGIS 통계값이 5 이하 등으로 비공개(N/A) 처리된 경우 DB에 null로 남음(CLAUDE.md
    // "아직 결정되지 않은 사항") — 로깅 가이드: N/A 값 처리는 WARN + 어떤 필드인지 기록.
    // populationStat/competitionStat row 자체가 없을 수도 있음(ScoreQueryService가
    // .orElse(null) 처리) — 그 경우도 통째로 누락 필드로 기록.
    if (!detail.populationStat) {
      missing.push('populationStat');
    } else {
      if (detail.populationStat.totalPopulation === null) missing.push('populationStat.totalPopulation');
      if (detail.populationStat.density === null) missing.push('populationStat.density');
      if (detail.populationStat.householdCount == null) missing.push('populationStat.householdCount');
      if (detail.populationStat.avgHouseholdSize == null) missing.push('populationStat.avgHouseholdSize');
    }
    if (!detail.competitionStat) missing.push('competitionStat');
    return missing;
  }, [detail]);

  useEffect(() => {
    if (missingFields.length > 0) {
      // 로깅 가이드: 상세 패널 데이터 누락 시 warn, 어떤 브레이크다운 필드가 누락됐는지 남김
      console.warn('[DetailPanel] 브레이크다운 필드 누락', {
        regionCode,
        industryCode,
        missingFields,
      });
    }
  }, [missingFields, regionCode, industryCode]);

  if (!industryCode || !regionCode) {
    return (
      <Panel>
        <EmptyState>업종과 지역을 선택하면 상세 정보가 표시됩니다.</EmptyState>
      </Panel>
    );
  }

  if (isLoading) {
    return (
      <Panel>
        <EmptyState>상세 정보를 불러오는 중...</EmptyState>
      </Panel>
    );
  }

  if (isNotFound) {
    return (
      <Panel>
        <EmptyState>
          아직 이 지역 · 업종 조합의 집계 데이터가 없습니다. 배치 작업 완료 후 다시 확인해주세요.
        </EmptyState>
      </Panel>
    );
  }

  if (isError || !detail) {
    return (
      <Panel>
        <EmptyState>상세 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</EmptyState>
      </Panel>
    );
  }

  const chartData = [
    { name: '인구 규모', score: detail.populationScore },
    { name: '가구 구조', score: detail.householdScore },
    { name: '경쟁 밀집도', score: detail.densityScore },
  ];

  return (
    <Panel>
      <TitleRow>
        <RegionTitle>
          {detail.regionName} · {detail.industryName}
        </RegionTitle>
        {rankingItem && (
          <TierBadge $color={getAttractivenessTierColor(rankingItem.attractivenessTier, theme)}>
            {ATTRACTIVENESS_TIER_ICON[rankingItem.attractivenessTier]} {formatPercentileLabel(rankingItem.percentileRank)}{' '}
            · {ATTRACTIVENESS_TIER_LABEL[rankingItem.attractivenessTier]}
          </TierBadge>
        )}
        <TotalScoreSecondary>
          종합 점수
          <TotalScoreValue $color={getScoreColor(detail.totalScore, theme)}>{detail.totalScore}</TotalScoreValue>
        </TotalScoreSecondary>
      </TitleRow>

      <BreakdownGrid>
        <BreakdownCard>
          <BreakdownLabel>① 인구 규모</BreakdownLabel>
          <BreakdownScore $color={getScoreColor(detail.populationScore, theme)}>
            {detail.populationScore}
          </BreakdownScore>
          <RawValue>
            총인구{' '}
            {detail.populationStat?.totalPopulation != null
              ? `${detail.populationStat.totalPopulation.toLocaleString()}명`
              : NA}
          </RawValue>
          <RawValue>
            인구 밀도{' '}
            {detail.populationStat?.density != null ? detail.populationStat.density.toLocaleString() : NA}
          </RawValue>
        </BreakdownCard>

        <BreakdownCard>
          <BreakdownLabel>② 가구 구조</BreakdownLabel>
          <BreakdownScore $color={getScoreColor(detail.householdScore, theme)}>{detail.householdScore}</BreakdownScore>
          <RawValue>
            가구 수{' '}
            {detail.populationStat?.householdCount != null
              ? `${detail.populationStat.householdCount.toLocaleString()}가구`
              : NA}
          </RawValue>
          <RawValue>
            평균 가구원 수{' '}
            {detail.populationStat?.avgHouseholdSize != null
              ? detail.populationStat.avgHouseholdSize.toFixed(2)
              : NA}
          </RawValue>
        </BreakdownCard>

        <BreakdownCard>
          <BreakdownLabel>③ 경쟁 밀집도</BreakdownLabel>
          <BreakdownScore $color={getScoreColor(detail.densityScore, theme)}>
            {detail.densityScore}
          </BreakdownScore>
          <RawValue>
            동일 업종 업소 수{' '}
            {detail.competitionStat != null ? `${detail.competitionStat.storeCount.toLocaleString()}개` : NA}
          </RawValue>
          <RawValue>기준일 {detail.competitionStat?.snapshotDate ?? NA}</RawValue>
        </BreakdownCard>
      </BreakdownGrid>

      <ChartWrapper>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis domain={[0, 100]} />
            <Tooltip />
            <Bar dataKey="score" fill={theme.colors.primary} radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </ChartWrapper>
    </Panel>
  );
}
