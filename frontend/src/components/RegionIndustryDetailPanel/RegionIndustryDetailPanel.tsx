import { useEffect, useMemo, useState } from 'react';
import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import styled, { useTheme } from 'styled-components';
import { ApiError } from '../../api/client';
import { findWeightPercent, useScoreWeights } from '../../api/scoreWeights';
import { useRanking, useScoreDetail } from '../../api/scores';
import { useStores } from '../../api/stores';
import { useSelection } from '../../context/SelectionContext';
import type { StoreItem } from '../../types/domain';
import { FavoriteStar } from '../FavoriteStar/FavoriteStar';
import { StoreDetailModal } from '../StoreDetailModal/StoreDetailModal';
import {
  ATTRACTIVENESS_TIER_ICON,
  ATTRACTIVENESS_TIER_LABEL,
  formatPercentileLabel,
  getAttractivenessTierColor,
} from '../../styles/attractivenessTier';
import { getScoreScaleColor, INSUFFICIENT_SAMPLE_LABEL } from '../../styles/scoreScale';

const NA = 'N/A';

const PanelRoot = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: ${({ theme }) => theme.spacing.xl};
  overflow-y: auto;
`;

const CloseButton = styled.button`
  align-self: flex-end;
  border: none;
  background: none;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  padding: ${({ theme }) => theme.spacing.xs};
  margin: -${({ theme }) => theme.spacing.xs};

  &:hover {
    color: ${({ theme }) => theme.colors.textPrimary};
  }
`;

const EmptyState = styled.div`
  margin: auto 0;
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: ${({ theme }) => theme.typography.body.size};
  line-height: ${({ theme }) => theme.typography.body.lineHeight};
`;

const HeaderSection = styled.div`
  margin-top: ${({ theme }) => theme.spacing.sm};
`;

/** 지역·업종 제목과 즐겨찾기 별표를 한 줄에 둔다 - 별표는 우측 정렬. */
const TitleRow = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: ${({ theme }) => theme.spacing.sm};
  margin-bottom: ${({ theme }) => theme.spacing.sm};
`;

const RegionTitle = styled.h1`
  margin: 0;
  font-size: ${({ theme }) => theme.typography.h1.size};
  font-weight: ${({ theme }) => theme.typography.h1.weight};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

/**
 * 등급색은 테두리에만 쓴다 - GOOD/AVERAGE 단계의 scoreScale 색은 흰 배경 위
 * 텍스트로 쓰면 WCAG 대비 3~4:1 안팎이라 작은 글자에서 잘 안 읽힌다(실측).
 * 텍스트는 항상 textPrimary로 고정해 가독성을 보장한다.
 */
const TierBadge = styled.div<{ $color: string }>`
  display: inline-flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.xs};
  width: fit-content;
  padding: ${({ theme }) => `${theme.spacing.xs} ${theme.spacing.md}`};
  border-radius: ${({ theme }) => theme.radius.pill};
  border: 1.5px solid ${({ $color }) => $color};
  color: ${({ theme }) => theme.colors.textPrimary};
  font-weight: ${({ theme }) => theme.typography.h3.weight};
  font-size: ${({ theme }) => theme.typography.body.size};
`;

const TierHint = styled.p`
  margin: ${({ theme }) => theme.spacing.xs} 0 0;
  font-size: ${({ theme }) => theme.typography.caption.size};
  color: ${({ theme }) => theme.colors.textSecondary};
  line-height: ${({ theme }) => theme.typography.caption.lineHeight};
`;

/** 화면당 유일하게 크게 강조하는 핵심 메시지 - 종합 점수. */
const ScoreHero = styled.div`
  margin-top: ${({ theme }) => theme.spacing.lg};
  padding: ${({ theme }) => theme.spacing.lg} 0;
`;

const ScoreHeroLabel = styled.div`
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  color: ${({ theme }) => theme.colors.textSecondary};
`;

/**
 * 화면당 유일하게 크게 강조하는 핵심 숫자라 항상 textPrimary(짙은 무채색)로
 * 고정한다 - scoreScale 색은 옅은 구간(0~40점대)에서 흰 배경 대비 1~2:1까지
 * 떨어져(WCAG 실측) 낮은 점수 지역일수록 숫자가 안 보이는 문제가 있었다.
 * 점수 구간 색상 표현은 지도 배지·차트 막대·등급 점(TierDot)에서 이미 하고
 * 있으므로 이 숫자까지 색을 입힐 필요는 없다.
 */
const ScoreHeroValue = styled.div`
  font-size: ${({ theme }) => theme.typography.display.size};
  font-weight: ${({ theme }) => theme.typography.display.weight};
  line-height: ${({ theme }) => theme.typography.display.lineHeight};
  color: ${({ theme }) => theme.colors.textPrimary};
  letter-spacing: -0.02em;
`;

const ScoreHeroSuffix = styled.span`
  font-size: ${({ theme }) => theme.typography.h2.size};
  font-weight: ${({ theme }) => theme.typography.h3.weight};
  color: ${({ theme }) => theme.colors.textSecondary};
  margin-left: ${({ theme }) => theme.spacing.xs};
`;

const CardsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: ${({ theme }) => theme.spacing.md};
  margin-top: ${({ theme }) => theme.spacing.md};

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
`;

const Card = styled.div`
  display: flex;
  flex-direction: column;
  background: ${({ theme }) => theme.colors.surface};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radius.md};
  padding: ${({ theme }) => theme.spacing.lg};
  box-shadow: ${({ theme }) => theme.shadow.card};
`;

const CardTitle = styled.h2`
  margin: 0;
  font-size: ${({ theme }) => theme.typography.h2.size};
  font-weight: ${({ theme }) => theme.typography.h2.weight};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const CardSubtitle = styled.p`
  margin: ${({ theme }) => theme.spacing.xs} 0 0;
  font-size: ${({ theme }) => theme.typography.caption.size};
  color: ${({ theme }) => theme.colors.textSecondary};
  line-height: ${({ theme }) => theme.typography.caption.lineHeight};
`;

const ChartWrapper = styled.div`
  height: 140px;
  margin-top: ${({ theme }) => theme.spacing.sm};
`;

const InsufficientChartPlaceholder = styled.div`
  height: 140px;
  margin-top: ${({ theme }) => theme.spacing.sm};
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: ${({ theme }) => theme.radius.sm};
  background: ${({ theme }) => theme.colors.backgroundAlt};
  color: ${({ theme }) => theme.colors.textTertiary};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-weight: ${({ theme }) => theme.typography.bodySmall.weight};
  text-align: center;
  padding: ${({ theme }) => theme.spacing.md};
`;

const RawValueList = styled.dl`
  margin: ${({ theme }) => theme.spacing.sm} 0 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const RawValueRow = styled.div`
  display: flex;
  justify-content: space-between;
  gap: ${({ theme }) => theme.spacing.sm};
  font-size: ${({ theme }) => theme.typography.caption.size};
  color: ${({ theme }) => theme.colors.textSecondary};

  dt {
    color: ${({ theme }) => theme.colors.textSecondary};
  }

  dd {
    margin: 0;
    color: ${({ theme }) => theme.colors.textPrimary};
    font-weight: ${({ theme }) => theme.typography.weight.medium};
    text-align: right;
  }
`;

const CardHint = styled.p`
  margin: ${({ theme }) => theme.spacing.sm} 0 0;
  font-size: ${({ theme }) => theme.typography.caption.size};
  color: ${({ theme }) => theme.colors.textSecondary};
  line-height: ${({ theme }) => theme.typography.caption.lineHeight};
`;

/**
 * 가중치 안내는 "산출 근거 불투명성 문제를 사전에 해소하기 위한 필수 요소"라 생략
 * 금지 - 카드 하단에 margin-top: auto로 고정해 항상 보이게 한다.
 */
const WeightNotice = styled.p`
  margin: ${({ theme }) => theme.spacing.md} 0 0 auto;
  margin-top: auto;
  padding-top: ${({ theme }) => theme.spacing.sm};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  font-size: ${({ theme }) => theme.typography.caption.size};
  color: ${({ theme }) => theme.colors.textSecondary};
  line-height: ${({ theme }) => theme.typography.caption.lineHeight};
  width: 100%;
`;

/**
 * NEUTRAL 업종(연령적합도 카드 자체가 없는 업종)일 때 카드 밖(PanelRoot 최하단)에
 * 노출하는 안내 - CardHint와 스타일은 동일하지만 카드 안이 아니라 카드 자체가 없는
 * 상황을 설명하는 문맥이라 별도 이름으로 둔다(TierHint/CardHint가 이미 같은 CSS를
 * 문맥별로 따로 두고 있는 것과 동일한 패턴 - 신규 색상/토큰은 추가하지 않는다).
 */
const NeutralAgeMetricNotice = styled.p`
  margin: ${({ theme }) => theme.spacing.md} 0 0;
  font-size: ${({ theme }) => theme.typography.caption.size};
  color: ${({ theme }) => theme.colors.textSecondary};
  line-height: ${({ theme }) => theme.typography.caption.lineHeight};
`;

const CardTitleRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.xs};
`;

/**
 * 별도 Tooltip 컴포넌트를 새로 만들지 않고, 이미 이 코드베이스에서 쓰고 있는
 * 네이티브 title 속성 호버 툴팁 패턴(MapDashboard의 TierDot)을 그대로 재사용한다 -
 * 디자인 시스템에 신규 요소를 추가하지 말라는 제약과도 맞다.
 */
const InfoIcon = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: ${({ theme }) => theme.colors.backgroundAlt};
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 11px;
  font-weight: ${({ theme }) => theme.typography.weight.semibold};
  cursor: help;
`;

const AGE_DIRECTION_DESCRIPTION: Record<'POSITIVE' | 'NEGATIVE', string> = {
  POSITIVE: '20~30대 비율이 높을수록 유리한 업종입니다',
  NEGATIVE: '고령층 비율이 높을수록 유리한 업종입니다',
};

const AGE_STAT_SOURCE_TOOLTIP =
  '이 지표는 주민등록 인구(KOSIS) 기준으로 계산돼요. 다른 지표(인구 규모·가구 구조·경쟁 여유도)는 추계 인구(SGIS) 기준이라 두 통계의 인구수가 정확히 일치하지 않을 수 있어요.';

function useWeightNoticeText(): string {
  const { data: weights, isLoading, isError } = useScoreWeights();

  if (isLoading) return '가중치 정보를 불러오는 중...';
  if (isError) return '가중치 정보를 불러오지 못했습니다. 새로고침 후 다시 시도해주세요.';

  const demandPercent = findWeightPercent(weights, 'DEMAND_WEIGHT');
  const supplyPercent = findWeightPercent(weights, 'SUPPLY_WEIGHT');
  if (demandPercent === null || supplyPercent === null) {
    return '가중치 설정을 찾을 수 없습니다 (SCORE_WEIGHT_CONFIG 시딩 확인 필요).';
  }
  return `이 지역·업종 조합에는 수요 ${demandPercent}% · 공급 ${supplyPercent}% 가중치가 적용됐어요.`;
}

/**
 * AGE_WEIGHT(DIRECTIONAL 업종에서 적용되는 4번째 리프 가중치)도 수요/공급과 동일하게
 * SCORE_WEIGHT_CONFIG에서 조회한 값만 쓴다 - 0.25를 코드에 매직 넘버로 넣지 않는다
 * (CLAUDE.md "가중치 숫자를 매직 넘버로 코드에 넣지 말 것").
 */
function useAgeWeightNoticeText(): string {
  const { data: weights, isLoading, isError } = useScoreWeights();

  if (isLoading) return '가중치 정보를 불러오는 중...';
  if (isError) return '가중치 정보를 불러오지 못했습니다. 새로고침 후 다시 시도해주세요.';

  const agePercent = findWeightPercent(weights, 'AGE_WEIGHT');
  if (agePercent === null) {
    return '가중치 설정을 찾을 수 없습니다 (SCORE_WEIGHT_CONFIG 시딩 확인 필요).';
  }
  return `이 업종에는 연령적합도 ${agePercent}% 가중치가 추가로 적용됐어요.`;
}

/** 선택 지역×업종의 개별 가게 목록 섹션. 항목 hover는 지도 마커 강조(onHoverStore)와
 * 연동되고, 클릭은 StoreDetailModal을 연다. 가게가 수백 개일 수 있어 스크롤로 가둔다. */
const StoreSection = styled.section`
  margin-top: ${({ theme }) => theme.spacing.lg};
  padding-top: ${({ theme }) => theme.spacing.lg};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
`;

const StoreSectionTitle = styled.h3`
  margin: 0 0 ${({ theme }) => theme.spacing.sm};
  font-size: ${({ theme }) => theme.typography.h3.size};
  font-weight: ${({ theme }) => theme.typography.h3.weight};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const StoreCountLabel = styled.span`
  margin-left: ${({ theme }) => theme.spacing.xs};
  font-size: ${({ theme }) => theme.typography.caption.size};
  font-weight: ${({ theme }) => theme.typography.weight.regular};
  color: ${({ theme }) => theme.colors.textSecondary};
`;

const StoreListScroll = styled.div`
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radius.sm};
`;

const StoreRow = styled.button`
  width: 100%;
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  border: none;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  color: ${({ theme }) => theme.colors.textPrimary};

  &:last-child {
    border-bottom: none;
  }
  &:hover {
    background: ${({ theme }) => theme.colors.accentSurface};
  }
`;

const StoreRowName = styled.span`
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const StoreRowChevron = styled.span`
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: ${({ theme }) => theme.typography.caption.size};
`;

const StoreEmpty = styled.div`
  padding: ${({ theme }) => theme.spacing.md};
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
`;

interface RegionIndustryDetailPanelProps {
  /** 목록 항목 hover 시 지도의 해당 가게 마커를 강조하도록 MapDashboard가 넘기는 콜백. */
  onHoverStore?: (bizesId: string | null) => void;
}

export function RegionIndustryDetailPanel({ onHoverStore }: RegionIndustryDetailPanelProps = {}) {
  const { industryCode, regionCode, setRegionCode } = useSelection();
  const { data: detail, isLoading, isError, error } = useScoreDetail(regionCode, industryCode);
  const isNotFound = error instanceof ApiError && error.status === 404;
  const theme = useTheme();
  const weightNoticeText = useWeightNoticeText();
  const ageWeightNoticeText = useAgeWeightNoticeText();

  // ScoreDetailResponse엔 percentileRank/attractivenessTier가 없다 - MapDashboard가
  // 이미 같은 industryCode로 랭킹을 불러와 있어 TanStack Query 캐시를 재사용한다.
  const { data: ranking } = useRanking(industryCode);
  const rankingItem = useMemo(
    () => ranking?.find((item) => item.regionCode === regionCode) ?? null,
    [ranking, regionCode],
  );

  // 개별 가게 목록 - MapDashboard도 같은 useStores를 호출하지만 queryKey가 같아
  // TanStack Query 캐시가 공유돼 네트워크 요청은 1회다(중복 호출 아님).
  const { data: stores, isLoading: storesLoading } = useStores(regionCode, industryCode);
  const [selectedStore, setSelectedStore] = useState<StoreItem | null>(null);

  // 지역/업종이 바뀌거나 패널이 사라질 때 지도에 남은 hover 강조를 확실히 해제한다
  // (목록 행 mouseleave가 안 뜨는 경우 대비).
  useEffect(() => {
    return () => onHoverStore?.(null);
  }, [regionCode, industryCode, onHoverStore]);

  // 지역/업종이 바뀌면 이전 가게 모달은 더 이상 유효하지 않으므로 닫는다.
  useEffect(() => {
    setSelectedStore(null);
  }, [regionCode, industryCode]);

  const missingFields = useMemo(() => {
    if (!detail) return [];
    const missing: string[] = [];
    if (!detail.populationStat) {
      missing.push('populationStat');
    } else {
      if (detail.populationStat.totalPopulation === null) missing.push('populationStat.totalPopulation');
      if (detail.populationStat.density === null) missing.push('populationStat.density');
      if (detail.populationStat.householdCount == null) missing.push('populationStat.householdCount');
      if (detail.populationStat.avgHouseholdSize == null) missing.push('populationStat.avgHouseholdSize');
    }
    if (!detail.competitionStat) missing.push('competitionStat');
    if (detail.densityScore === null) missing.push('densityScore(인구 표본 부족)');
    if (detail.ageDirection && detail.ageStat.ageScore === null) {
      missing.push('ageStat.ageScore(연령 통계 표본 부족)');
    }
    return missing;
  }, [detail]);

  useEffect(() => {
    if (missingFields.length > 0) {
      console.warn('[RegionIndustryDetailPanel] 브레이크다운 필드 누락', {
        regionCode,
        industryCode,
        missingFields,
      });
    }
  }, [missingFields, regionCode, industryCode]);

  const closeButton = (
    <CloseButton type="button" onClick={() => setRegionCode(null)} aria-label="상세 패널 닫기">
      ✕
    </CloseButton>
  );

  if (!industryCode || !regionCode) {
    return (
      <PanelRoot>
        {closeButton}
        <EmptyState>업종과 지역을 선택하면 상세 정보가 표시됩니다.</EmptyState>
      </PanelRoot>
    );
  }

  if (isLoading) {
    return (
      <PanelRoot>
        {closeButton}
        <EmptyState>상세 정보를 불러오는 중...</EmptyState>
      </PanelRoot>
    );
  }

  if (isNotFound) {
    return (
      <PanelRoot>
        {closeButton}
        <EmptyState>
          아직 이 지역 · 업종 조합의 집계 데이터가 없습니다. 배치 작업 완료 후 다시 확인해주세요.
        </EmptyState>
      </PanelRoot>
    );
  }

  if (isError || !detail) {
    return (
      <PanelRoot>
        {closeButton}
        <EmptyState>상세 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</EmptyState>
      </PanelRoot>
    );
  }

  const demandChartData = [
    { name: '인구 규모', score: detail.populationScore },
    { name: '가구 구조', score: detail.householdScore },
  ];
  const supplyChartData =
    detail.densityScore !== null ? [{ name: '경쟁 여유도', score: detail.densityScore }] : [];
  const ageChartData =
    detail.ageStat.ageScore !== null ? [{ name: '연령 적합도', score: detail.ageStat.ageScore }] : [];

  return (
    <PanelRoot>
      {closeButton}

      <HeaderSection>
        <TitleRow>
          <RegionTitle>
            {detail.regionName} · {detail.industryName}
          </RegionTitle>
          <FavoriteStar regionCode={regionCode} industryCode={industryCode} size="md" />
        </TitleRow>
        {rankingItem && (
          <>
            <TierBadge $color={getAttractivenessTierColor(rankingItem.attractivenessTier, theme)}>
              {ATTRACTIVENESS_TIER_ICON[rankingItem.attractivenessTier]} {formatPercentileLabel(rankingItem.percentileRank)}
              {' · '}
              {ATTRACTIVENESS_TIER_LABEL[rankingItem.attractivenessTier]}
            </TierBadge>
            <TierHint>
              단순히 "인기 있는 곳"이 아니라, 수요는 충분하면서 그 수요 대비 경쟁은 상대적으로
              여유 있는 곳을 뜻해요.
            </TierHint>
          </>
        )}
      </HeaderSection>

      <ScoreHero>
        <ScoreHeroLabel>종합 점수</ScoreHeroLabel>
        <div>
          <ScoreHeroValue>
            {detail.totalScore !== null ? detail.totalScore : INSUFFICIENT_SAMPLE_LABEL}
          </ScoreHeroValue>
          {detail.totalScore !== null && <ScoreHeroSuffix>/ 100</ScoreHeroSuffix>}
        </div>
        {detail.totalScore === null && (
          <TierHint>
            이 지역은 인구 표본이 너무 작아(100명 미만) 경쟁 여유도를 산출할 수 없고, 그 결과
            종합 점수도 함께 제공되지 않아요.
          </TierHint>
        )}
      </ScoreHero>

      <CardsGrid>
        <Card>
          <CardTitle>수요</CardTitle>
          <CardSubtitle>인구 규모 + 가구 구조 (SGIS 통계 기반)</CardSubtitle>
          <ChartWrapper>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={demandChartData}>
                <XAxis dataKey="name" interval={0} tick={{ fontSize: 11, fill: theme.colors.textSecondary }} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 11, fill: theme.colors.textSecondary }} width={28} />
                <Tooltip />
                <Bar dataKey="score" radius={[4, 4, 0, 0]}>
                  {demandChartData.map((entry) => (
                    <Cell key={entry.name} fill={getScoreScaleColor(entry.score, theme)} stroke={theme.colors.border} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </ChartWrapper>
          <RawValueList>
            <RawValueRow>
              <dt>총인구</dt>
              <dd>
                {detail.populationStat?.totalPopulation != null
                  ? `${detail.populationStat.totalPopulation.toLocaleString()}명`
                  : NA}
              </dd>
            </RawValueRow>
            <RawValueRow>
              <dt>인구 밀도</dt>
              <dd>{detail.populationStat?.density != null ? detail.populationStat.density.toLocaleString() : NA}</dd>
            </RawValueRow>
            <RawValueRow>
              <dt>가구 수</dt>
              <dd>
                {detail.populationStat?.householdCount != null
                  ? `${detail.populationStat.householdCount.toLocaleString()}가구`
                  : NA}
              </dd>
            </RawValueRow>
            <RawValueRow>
              <dt>평균 가구원 수</dt>
              <dd>
                {detail.populationStat?.avgHouseholdSize != null
                  ? detail.populationStat.avgHouseholdSize.toFixed(2)
                  : NA}
              </dd>
            </RawValueRow>
          </RawValueList>
          <WeightNotice>{weightNoticeText}</WeightNotice>
        </Card>

        <Card>
          <CardTitle>공급</CardTitle>
          <CardSubtitle>경쟁 여유도 (소상공인시장진흥공단 상가정보 기반)</CardSubtitle>
          {detail.densityScore !== null ? (
            <ChartWrapper>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={supplyChartData}>
                  <XAxis dataKey="name" interval={0} tick={{ fontSize: 11, fill: theme.colors.textSecondary }} />
                  <YAxis domain={[0, 100]} tick={{ fontSize: 11, fill: theme.colors.textSecondary }} width={28} />
                  <Tooltip />
                  <Bar dataKey="score" radius={[4, 4, 0, 0]}>
                    {supplyChartData.map((entry) => (
                      <Cell key={entry.name} fill={getScoreScaleColor(entry.score, theme)} stroke={theme.colors.border} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </ChartWrapper>
          ) : (
            <InsufficientChartPlaceholder>
              {INSUFFICIENT_SAMPLE_LABEL}
              <br />
              (인구 표본 100명 미만이라 산출하지 않음)
            </InsufficientChartPlaceholder>
          )}
          {/*
            "동일 업종 업소 117개 (1만명당 22.6)"처럼 한 줄에 붙여 쓰면 어색한
            줄바꿈 지점에서 "업종"과 "업소"가 붙어 보여 오독 위험이 있었다(피드백) -
            "무엇을 세는 값인지"를 레이블로 분리: 선택된 업종은 업종 행, 그 업종의
            가게 개수는 별도 가게 수 행으로 나눈다. 지도 클러스터 툴팁과 동일하게
            "가게"라는 단어로 통일(업소/매장/점포 혼용 금지).
          */}
          <RawValueList>
            <RawValueRow>
              <dt>업종</dt>
              <dd>{detail.industryName} (선택됨)</dd>
            </RawValueRow>
            <RawValueRow>
              <dt>가게 수</dt>
              <dd>
                {detail.competitionStat != null
                  ? `${detail.competitionStat.storeCount.toLocaleString()}개`
                  : NA}
              </dd>
            </RawValueRow>
            <RawValueRow>
              <dt>인구 1만명당 가게 수</dt>
              <dd>
                {detail.competitionStat?.storeCountPerCapita != null
                  ? `${detail.competitionStat.storeCountPerCapita.toFixed(1)}개`
                  : NA}
              </dd>
            </RawValueRow>
            <RawValueRow>
              <dt>기준일</dt>
              <dd>{detail.competitionStat?.snapshotDate ?? NA}</dd>
            </RawValueRow>
          </RawValueList>
          <CardHint>
            {detail.densityScore !== null
              ? '점수가 높을수록 경쟁이 적어 여유 있음을 의미해요.'
              : '0점이 아니라 "산출 불가"예요 - 밀도 이상치가 다른 지역 순위를 왜곡하지 않도록 최소 인구 기준 미만은 제외합니다.'}
          </CardHint>
          <WeightNotice>{weightNoticeText}</WeightNotice>
        </Card>

        {/*
          ageDirection이 non-null이면(=이 업종엔 연령적합도 지표가 존재) 카드를 렌더링하고,
          그 안에서 ageScore가 null인 경우(인구 100명 미만 등 표본 부족)는 densityScore와
          동일한 기존 컨벤션(카드 자체를 숨기지 않고 InsufficientChartPlaceholder로 "산출
          불가"를 명시)을 그대로 따른다 - ageDirection이 이미 "이 업종엔 이 지표가 적용된다"는
          의미를 담고 있으므로, 표본 부족은 카드를 통째로 숨기기보다 그 사실 자체를 보여주는
          쪽이 사용자에게 더 많은 정보를 준다.
        */}
        {detail.ageDirection && (
          <Card>
            <CardTitleRow>
              <CardTitle>연령 적합도</CardTitle>
              <InfoIcon title={AGE_STAT_SOURCE_TOOLTIP} aria-label="통계 기준 안내">
                i
              </InfoIcon>
            </CardTitleRow>
            <CardSubtitle>20~39세 인구 비율 기반 ({detail.ageStat.dataSource})</CardSubtitle>
            {detail.ageStat.ageScore !== null ? (
              <ChartWrapper>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={ageChartData}>
                    <XAxis dataKey="name" interval={0} tick={{ fontSize: 11, fill: theme.colors.textSecondary }} />
                    <YAxis domain={[0, 100]} tick={{ fontSize: 11, fill: theme.colors.textSecondary }} width={28} />
                    <Tooltip />
                    <Bar dataKey="score" radius={[4, 4, 0, 0]}>
                      {ageChartData.map((entry) => (
                        <Cell key={entry.name} fill={getScoreScaleColor(entry.score, theme)} stroke={theme.colors.border} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </ChartWrapper>
            ) : (
              <InsufficientChartPlaceholder>
                {INSUFFICIENT_SAMPLE_LABEL}
                <br />
                (연령 통계 표본이 부족하거나 아직 집계되지 않았어요)
              </InsufficientChartPlaceholder>
            )}
            <RawValueList>
              <RawValueRow>
                <dt>20~39세 비율</dt>
                <dd>
                  {detail.ageStat.ageRatioPercent !== null ? `${detail.ageStat.ageRatioPercent}%` : NA}
                </dd>
              </RawValueRow>
            </RawValueList>
            <CardHint>{AGE_DIRECTION_DESCRIPTION[detail.ageDirection]}</CardHint>
            <WeightNotice>{ageWeightNoticeText}</WeightNotice>
          </Card>
        )}
      </CardsGrid>

      {/*
        NEUTRAL 업종(ageDirection === null)은 densityScore/totalScore가 null일 때처럼
        이유를 설명하는 문구 없이 카드가 그냥 없어지면, 업종을 바꿨을 때 카드 개수가
        조용히 줄어들어 "로딩 실패인가?"로 오해하기 쉽다(CLAUDE.md 대시보드 설계 원칙
        "왜 이 점수인가를 사용자가 확인할 수 있어야 한다"와 어긋남) - 카드를 새로 만들지
        않고 패널 하단에 짧은 설명만 추가한다.
      */}
      {!detail.ageDirection && (
        <NeutralAgeMetricNotice>이 업종은 연령 구성과 무관해 연령적합도 지표를 사용하지 않아요.</NeutralAgeMetricNotice>
      )}

      <StoreSection>
        <StoreSectionTitle>
          가게 목록
          {!storesLoading && stores != null && <StoreCountLabel>({stores.length}곳)</StoreCountLabel>}
        </StoreSectionTitle>
        {storesLoading ? (
          <StoreEmpty>가게 목록을 불러오는 중...</StoreEmpty>
        ) : !stores || stores.length === 0 ? (
          <StoreEmpty>표시할 가게가 없어요.</StoreEmpty>
        ) : (
          <StoreListScroll>
            {stores.map((store) => (
              <StoreRow
                key={store.bizesId}
                type="button"
                onMouseEnter={() => onHoverStore?.(store.bizesId)}
                onMouseLeave={() => onHoverStore?.(null)}
                onFocus={() => onHoverStore?.(store.bizesId)}
                onBlur={() => onHoverStore?.(null)}
                onClick={() => setSelectedStore(store)}
              >
                <StoreRowName>{store.bizesNm}</StoreRowName>
                <StoreRowChevron>상세 ›</StoreRowChevron>
              </StoreRow>
            ))}
          </StoreListScroll>
        )}
      </StoreSection>

      {selectedStore && (
        <StoreDetailModal
          store={selectedStore}
          regionName={detail.regionName}
          industryName={detail.industryName}
          onClose={() => setSelectedStore(null)}
        />
      )}
    </PanelRoot>
  );
}
