import { useEffect, useRef } from 'react';
import styled, { useTheme } from 'styled-components';
import { useRanking } from '../../api/scores';
import { useStores } from '../../api/stores';
import { useSelection } from '../../context/SelectionContext';
import { IndustrySelector } from '../IndustrySelector/IndustrySelector';
import { RegionIndustryDetailPanel } from '../RegionIndustryDetailPanel/RegionIndustryDetailPanel';
import { RegionSearchBox } from '../RegionSearchBox/RegionSearchBox';
import {
  ATTRACTIVENESS_TIER_ICON,
  ATTRACTIVENESS_TIER_LABEL,
  getAttractivenessTierColor,
} from '../../styles/attractivenessTier';
import { getScoreScaleColor, getScoreScaleTextColor } from '../../styles/scoreScale';
import type { AppTheme } from '../../styles/theme';
import type { RankingItem } from '../../types/domain';
import { useKakaoLoader } from './useKakaoLoader';

// 지도에 표시할 데이터가 아직 없을 때만 쓰는 기본 중심 좌표(대한민국 국토 중앙 근사치).
// 특정 지역명을 코드에 고정하는 것이 아니라, 좌표 데이터가 없을 때의 렌더링 fallback일 뿐임.
const DEFAULT_CENTER = { latitude: 36.5, longitude: 127.8 };
const DEFAULT_LEVEL = 13;
const SELECTED_LEVEL = 5;
const DETAIL_PANEL_WIDTH = '440px';

const Layout = styled.div`
  display: flex;
  height: 100%;
  background: ${({ theme }) => theme.colors.surface};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radius.lg};
  box-shadow: ${({ theme }) => theme.shadow.card};
  overflow: hidden;

  @media (max-width: ${({ theme }) => theme.breakpoint.tablet}) {
    flex-direction: column;
  }
`;

/**
 * 지도(60~70%)가 주된 캔버스가 되도록 사이드바는 고정 폭으로 좁게 둔다
 * (direction 3: "지도 중심 인터랙션"). CLAUDE.md가 확정한 "업종 선택 →
 * 랭킹 리스트 + 지도(양방향 연동) → 상세 패널" 흐름을 지키기 위해 업종
 * 토글과 랭킹 리스트를 이 사이드바에 함께 둔다.
 */
const Sidebar = styled.div`
  width: 360px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.backgroundAlt};

  @media (max-width: ${({ theme }) => theme.breakpoint.tablet}) {
    width: 100%;
    max-height: 40%;
    border-right: none;
    border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  }
`;

const SidebarHeader = styled.div`
  padding: ${({ theme }) => theme.spacing.lg};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.md};
`;

const RankingHeaderRow = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: ${({ theme }) => `${theme.spacing.md} ${theme.spacing.lg} ${theme.spacing.sm}`};
`;

const RankingTitle = styled.h2`
  margin: 0;
  font-size: ${({ theme }) => theme.typography.h3.size};
  font-weight: ${({ theme }) => theme.typography.h3.weight};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const RankingCount = styled.span`
  font-size: ${({ theme }) => theme.typography.caption.size};
  color: ${({ theme }) => theme.colors.textSecondary};
`;

const RankingScroll = styled.div`
  flex: 1;
  overflow-y: auto;
`;

const RankingRow = styled.button<{ $selected: boolean }>`
  width: 100%;
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.lg}`};
  border: none;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ $selected, theme }) => ($selected ? theme.colors.accentSurface : 'transparent')};
  cursor: pointer;
  text-align: left;
  font-family: inherit;

  &:hover {
    background: ${({ theme }) => theme.colors.accentSurface};
  }
`;

const RankingRank = styled.span`
  width: 20px;
  flex-shrink: 0;
  font-size: ${({ theme }) => theme.typography.caption.size};
  color: ${({ theme }) => theme.colors.textSecondary};
  font-weight: ${({ theme }) => theme.typography.weight.semibold};
`;

const TierDot = styled.span<{ $color: string }>`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: ${({ $color }) => $color};
`;

const RankingRegionName = styled.span`
  flex: 1;
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-weight: ${({ theme }) => theme.typography.weight.medium};
  color: ${({ theme }) => theme.colors.textPrimary};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

/**
 * textPrimary로 고정 - scoreScale을 텍스트 색으로 쓰면 낮은 점수 구간(0~40점대)이
 * 흰/연한 배경 위에서 WCAG 대비 1~2:1까지 떨어져 거의 안 보인다(실측 후 발견).
 * 점수 구간 색 표현은 왼쪽 TierDot이 담당한다.
 */
const RankingScore = styled.span`
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-weight: ${({ theme }) => theme.typography.weight.bold};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const SidebarEmptyState = styled.div`
  padding: ${({ theme }) => theme.spacing.xl} ${({ theme }) => theme.spacing.lg};
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  line-height: ${({ theme }) => theme.typography.body.lineHeight};
`;

const MapCanvasArea = styled.div`
  flex: 1;
  position: relative;
  min-height: 420px;
`;

const MapContainer = styled.div`
  position: absolute;
  inset: 0;
`;

const EmptyOverlay = styled.div`
  position: absolute;
  inset: 0;
  /* Kakao Maps SDK가 타일/오버레이 레이어에 z-index를 최대 2까지 직접 지정한다
     (헤드리스 브라우저로 실제 렌더링 확인함) - 그보다 높여야 이 안내 문구가
     지도 타일에 가려지지 않는다. */
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.spacing.xl};
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: ${({ theme }) => theme.typography.body.size};
  background: ${({ theme }) => theme.colors.surface};
  opacity: 0.94;
  pointer-events: none;
`;

/** 개별 업소 좌표 출처 표기 - 소상공인시장진흥공단 상가업소 API(CLAUDE.md 확정 API 2). */
const AttributionBadge = styled.div`
  position: absolute;
  bottom: ${({ theme }) => theme.spacing.sm};
  right: ${({ theme }) => theme.spacing.sm};
  z-index: 5;
  padding: 2px ${({ theme }) => theme.spacing.sm};
  border-radius: ${({ theme }) => theme.radius.sm};
  background: rgba(255, 255, 255, 0.85);
  color: ${({ theme }) => theme.colors.textTertiary};
  font-size: ${({ theme }) => theme.typography.caption.size};
  pointer-events: none;
`;

const FallbackMessage = styled.div`
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.spacing.xl};
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
`;

/** 지역 클릭 시 지도 우측에서 슬라이드 인 - 항상 마운트해 두고 transform으로만 여닫아 재요청 깜빡임을 없앤다. */
const SlidePanel = styled.div<{ $open: boolean }>`
  position: absolute;
  top: 0;
  right: 0;
  height: 100%;
  width: ${DETAIL_PANEL_WIDTH};
  max-width: 100%;
  background: ${({ theme }) => theme.colors.surface};
  border-left: 1px solid ${({ theme }) => theme.colors.border};
  box-shadow: ${({ theme }) => theme.shadow.panel};
  z-index: 20;
  transform: translateX(${({ $open }) => ($open ? '0' : '100%')});
  transition: transform 0.25s ease;
  pointer-events: ${({ $open }) => ($open ? 'auto' : 'none')};
`;

function buildScoreBadgeElement(
  item: RankingItem,
  theme: AppTheme,
  isSelected: boolean,
  onClick: () => void,
): HTMLDivElement {
  const el = document.createElement('div');
  el.textContent = String(Math.round(item.totalScore));
  el.title = `${item.regionName} · ${ATTRACTIVENESS_TIER_LABEL[item.attractivenessTier]}`;
  el.style.display = 'flex';
  el.style.alignItems = 'center';
  el.style.justifyContent = 'center';
  el.style.width = isSelected ? '40px' : '34px';
  el.style.height = isSelected ? '40px' : '34px';
  el.style.borderRadius = '50%';
  el.style.background = getScoreScaleColor(item.totalScore, theme);
  el.style.color = getScoreScaleTextColor(item.totalScore, theme);
  el.style.fontFamily = theme.typography.fontFamily;
  el.style.fontSize = '13px';
  el.style.fontWeight = String(theme.typography.weight.bold);
  el.style.border = `2px solid ${isSelected ? theme.colors.accent : theme.colors.surface}`;
  el.style.boxShadow = theme.shadow.card;
  el.style.cursor = 'pointer';
  el.style.transform = 'translate(-50%, -50%)';
  el.addEventListener('click', onClick);
  return el;
}

/**
 * 개별 업소 마커 아이콘 - 지역 배지(색상 스케일 원+숫자, CustomOverlay)와 확실히
 * 구분되는 작은 무채색 점. CustomOverlay(지역 배지 방식)를 지역당 최대 수천 개인
 * 업소에도 그대로 쓰면 실측 4초 넘게 걸리는 렌더링 지연이 있어(성능 확인 결과),
 * 가벼운 네이티브 Marker + MarkerClusterer 조합으로 바꿨다 - MarkerImage는
 * DOM 엘리먼트가 아니라 이미지 한 장이라 개수가 늘어도 비용이 훨씬 작다.
 */
function buildStoreMarkerImage(theme: AppTheme): kakao.maps.MarkerImage {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">` +
    `<circle cx="5" cy="5" r="4" fill="${theme.colors.textPrimary}" stroke="${theme.colors.surface}" stroke-width="1.5"/>` +
    `</svg>`;
  const src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
  return new window.kakao.maps.MarkerImage(src, new window.kakao.maps.Size(10, 10), {
    offset: new window.kakao.maps.Point(5, 5),
  });
}

function hexToRgba(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/**
 * 클러스터 배지 개수 구간 경계 - styles 배열(4단계)과 반드시 같은 순서로 맞춘다.
 * 10개 미만 / 10~49개 / 50~99개 / 100개 이상, 총 4단계로 "가게 개수" 양적 차이를
 * 크기·색 농도로 함께 드러낸다(라벨 없이 숫자만 떠 있어 업종 개수인지 가게 개수인지
 * 헷갈렸던 피드백 반영 - 클러스터 hover/click 시 뜨는 툴팁(buildClusterTooltipContent)이
 * "가게 N개"로 명확히 밝혀준다).
 */
const CLUSTER_SIZE_TIERS = [10, 50, 100];

/**
 * MarkerClusterer 기본 스타일은 카카오 기본값(노랑/초록 신호등식)이라 "강조색은
 * accent 하나만" 원칙에 맞지 않아 오버라이드 - 개수 구간(CLUSTER_SIZE_TIERS)에 따라
 * accent를 크기/불투명도만 다르게 써서 하나의 색 스케일로만 표현한다.
 */
function buildClustererStyles(theme: AppTheme): Partial<CSSStyleDeclaration>[] {
  const base: Partial<CSSStyleDeclaration> = {
    color: theme.colors.onAccent,
    textAlign: 'center',
    fontFamily: theme.typography.fontFamily,
    fontWeight: String(theme.typography.weight.bold),
  };
  return [
    { ...base, width: '28px', height: '28px', lineHeight: '28px', fontSize: '11px', borderRadius: '14px', background: hexToRgba(theme.colors.accent, 0.45) },
    { ...base, width: '36px', height: '36px', lineHeight: '36px', fontSize: '12px', borderRadius: '18px', background: hexToRgba(theme.colors.accent, 0.65) },
    { ...base, width: '44px', height: '44px', lineHeight: '44px', fontSize: '13px', borderRadius: '22px', background: hexToRgba(theme.colors.accent, 0.85) },
    { ...base, width: '52px', height: '52px', lineHeight: '52px', fontSize: '14px', borderRadius: '26px', background: hexToRgba(theme.colors.accent, 1) },
  ];
}

/** InfoWindow는 마커마다 만들지 않고 하나를 재사용 - content만 매번 바꿔 연다. */
function buildStoreInfoContent(bizesNm: string, theme: AppTheme): HTMLDivElement {
  const el = document.createElement('div');
  el.textContent = bizesNm;
  el.style.padding = '3px 8px';
  el.style.fontFamily = theme.typography.fontFamily;
  el.style.fontSize = '11px';
  el.style.fontWeight = String(theme.typography.weight.medium);
  el.style.background = theme.colors.textPrimary;
  el.style.color = theme.colors.onAccent;
  el.style.borderRadius = theme.radius.sm;
  el.style.whiteSpace = 'nowrap';
  return el;
}

/**
 * 클러스터 배지 hover/click 시 뜨는 툴팁 - "가게 47개"처럼 숫자가 무엇을 세는
 * 값인지 명시한다. 상세 패널의 "가게 수" 표기와 같은 단어("가게")로 통일.
 */
function buildClusterTooltipContent(count: number, theme: AppTheme): HTMLDivElement {
  const el = document.createElement('div');
  el.textContent = `가게 ${count.toLocaleString()}개`;
  el.style.padding = '3px 8px';
  el.style.fontFamily = theme.typography.fontFamily;
  el.style.fontSize = '11px';
  el.style.fontWeight = String(theme.typography.weight.medium);
  el.style.background = theme.colors.textPrimary;
  el.style.color = theme.colors.onAccent;
  el.style.borderRadius = theme.radius.sm;
  el.style.whiteSpace = 'nowrap';
  return el;
}

export function MapDashboard() {
  const { industryCode, regionCode, setRegionCode } = useSelection();
  const { data: ranking, isLoading, isError } = useRanking(industryCode);
  // 상세 패널이 열려있을 때만(regionCode && industryCode 둘 다 있을 때만) enabled -
  // 전체 랭킹 지도에서는 호출되지 않는다(StoreController 문서에 명시된 사용 조건).
  const { data: stores } = useStores(regionCode, industryCode);
  const kakaoStatus = useKakaoLoader();
  const theme = useTheme();

  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<kakao.maps.Map | null>(null);
  const overlaysRef = useRef<Map<string, kakao.maps.CustomOverlay>>(new Map());
  const storeMarkersRef = useRef<kakao.maps.Marker[]>([]);
  const storeClustererRef = useRef<kakao.maps.MarkerClusterer | null>(null);
  const storeInfoWindowRef = useRef<kakao.maps.InfoWindow | null>(null);
  const clusterInfoWindowRef = useRef<kakao.maps.InfoWindow | null>(null);
  const pinnedStoreIdRef = useRef<string | null>(null);
  const boundsFittedForIndustryRef = useRef<string | null>(null);

  // 지도 인스턴스 초기화 (SDK 준비된 이후 1회)
  useEffect(() => {
    if (kakaoStatus !== 'ready' || !containerRef.current || mapRef.current) {
      return;
    }

    mapRef.current = new window.kakao.maps.Map(containerRef.current, {
      center: new window.kakao.maps.LatLng(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude),
      level: DEFAULT_LEVEL,
    });
  }, [kakaoStatus]);

  // 랭킹 데이터/선택 지역이 바뀔 때마다 점수 배지 오버레이를 다시 그리기.
  // RankingItem.latitude/longitude는 좌표 시딩 전 지역은 null일 수 있으므로
  // 그런 지역만 개별적으로 오버레이 생성을 건너뛴다(전체를 막지 않음).
  useEffect(() => {
    const map = mapRef.current;
    if (kakaoStatus !== 'ready' || !map) {
      return;
    }

    overlaysRef.current.forEach((overlay) => overlay.setMap(null));
    overlaysRef.current.clear();

    (ranking ?? []).forEach((item) => {
      const { latitude, longitude } = item;
      if (latitude === null || longitude === null) {
        console.warn('[MapDashboard] 좌표 시딩 전(null)이라 점수 배지를 표시하지 못함', item.regionCode);
        return;
      }

      const element = buildScoreBadgeElement(item, theme, item.regionCode === regionCode, () =>
        setRegionCode(item.regionCode),
      );

      const overlay = new window.kakao.maps.CustomOverlay({
        position: new window.kakao.maps.LatLng(latitude, longitude),
        content: element,
        map,
        yAnchor: 0.5,
        xAnchor: 0.5,
      });

      overlaysRef.current.set(item.regionCode, overlay);
    });
  }, [ranking, kakaoStatus, regionCode, setRegionCode, theme]);

  // 상세 패널이 열려있을 때만(regionCode 선택 시) 그 지역·업종의 개별 업소 점을
  // 추가로 그린다 - 패널을 닫으면(regionCode === null) stores 쿼리 자체가
  // disabled로 꺼지고 아래에서 기존 마커를 전부 지워 지역 배지만 남긴다.
  // MarkerClusterer 사용(성능 확인 결과 - 아래 설명): 개별 setMap 대신
  // clusterer.clear()/addMarkers()로 일괄 관리한다.
  useEffect(() => {
    const map = mapRef.current;
    if (kakaoStatus !== 'ready' || !map) {
      return;
    }

    storeClustererRef.current?.clear();
    storeMarkersRef.current = [];
    storeInfoWindowRef.current?.close();
    clusterInfoWindowRef.current?.close();
    pinnedStoreIdRef.current = null;

    if (!regionCode || !industryCode) {
      return;
    }

    if (!storeInfoWindowRef.current) {
      storeInfoWindowRef.current = new window.kakao.maps.InfoWindow({ removable: false });
    }
    const infoWindow = storeInfoWindowRef.current;
    const markerImage = buildStoreMarkerImage(theme);

    const markers: kakao.maps.Marker[] = [];
    (stores ?? []).forEach((store) => {
      if (store.lat === null || store.lon === null) {
        console.warn('[MapDashboard] 좌표 없는 개별 업소라 마커를 표시하지 못함', store.bizesId);
        return;
      }

      const marker = new window.kakao.maps.Marker({
        position: new window.kakao.maps.LatLng(store.lat, store.lon),
        image: markerImage,
        title: store.bizesNm,
      });

      window.kakao.maps.event.addListener(marker, 'mouseover', () => {
        infoWindow.setContent(buildStoreInfoContent(store.bizesNm, theme));
        infoWindow.open(map, marker);
      });
      window.kakao.maps.event.addListener(marker, 'mouseout', () => {
        if (pinnedStoreIdRef.current !== store.bizesId) {
          infoWindow.close();
        }
      });
      window.kakao.maps.event.addListener(marker, 'click', () => {
        if (pinnedStoreIdRef.current === store.bizesId) {
          pinnedStoreIdRef.current = null;
          infoWindow.close();
          return;
        }
        pinnedStoreIdRef.current = store.bizesId;
        infoWindow.setContent(buildStoreInfoContent(store.bizesNm, theme));
        infoWindow.open(map, marker);
      });

      markers.push(marker);
    });

    // MarkerClusterer가 map에 표시/클러스터링을 대신 맡으므로 개별 marker.setMap
    // 호출은 하지 않는다(둘 다 하면 클러스터 밖에 중복으로 찍힘).
    if (!storeClustererRef.current) {
      const clusterer = new window.kakao.maps.MarkerClusterer({
        map,
        averageCenter: true,
        minLevel: 3,
        styles: buildClustererStyles(theme),
        calculator: CLUSTER_SIZE_TIERS,
      });

      if (!clusterInfoWindowRef.current) {
        clusterInfoWindowRef.current = new window.kakao.maps.InfoWindow({ removable: false });
      }
      const clusterInfoWindow = clusterInfoWindowRef.current;

      // 라벨 없는 숫자 배지만으로는 "가게 개수"인지 알기 어렵다는 피드백 반영 -
      // hover든 click이든 항상 "가게 N개" 툴팁으로 명확히 밝힌다(클릭은 기본
      // 줌인 동작도 그대로 유지 - disableClickZoom을 켜지 않았으므로 방해 없음).
      window.kakao.maps.event.addListener(clusterer, 'clusterover', (cluster) => {
        clusterInfoWindow.setContent(buildClusterTooltipContent(cluster.getSize(), theme));
        clusterInfoWindow.setPosition(cluster.getCenter());
        clusterInfoWindow.open(map);
      });
      window.kakao.maps.event.addListener(clusterer, 'clusterout', () => {
        clusterInfoWindow.close();
      });
      window.kakao.maps.event.addListener(clusterer, 'clusterclick', (cluster) => {
        clusterInfoWindow.setContent(buildClusterTooltipContent(cluster.getSize(), theme));
        clusterInfoWindow.setPosition(cluster.getCenter());
        clusterInfoWindow.open(map);
      });

      storeClustererRef.current = clusterer;
    }
    storeClustererRef.current.addMarkers(markers);
    storeMarkersRef.current = markers;
  }, [stores, regionCode, industryCode, kakaoStatus, theme]);

  // 업종을 고르면(=랭킹이 처음 도착하면) 기본 줌(전국)이 아니라 그 업종의 실제
  // 지역 분포에 맞춰 지도를 자동으로 프레이밍한다 - REGION에 폴리곤이 없어 경계를
  // 알 수 없으므로, 랭킹에 실린 좌표들을 감싸는 범위로 근사한다. 업종당 1회만
  // 맞추고(같은 업종 안에서 지역 클릭 시 아래 효과가 그 지역으로 다시 줌인한다),
  // 지역 선택을 지우고 업종만 다시 볼 때 매번 재프레이밍되지 않게 한다.
  useEffect(() => {
    const map = mapRef.current;
    if (kakaoStatus !== 'ready' || !map || !industryCode || !ranking || ranking.length === 0) {
      return;
    }
    if (boundsFittedForIndustryRef.current === industryCode) {
      return;
    }

    const bounds = new window.kakao.maps.LatLngBounds();
    let hasCoordinate = false;
    ranking.forEach((item) => {
      if (item.latitude !== null && item.longitude !== null) {
        bounds.extend(new window.kakao.maps.LatLng(item.latitude, item.longitude));
        hasCoordinate = true;
      }
    });

    if (hasCoordinate) {
      map.setBounds(bounds);
      boundsFittedForIndustryRef.current = industryCode;
    }
  }, [ranking, kakaoStatus, industryCode]);

  // 랭킹 리스트에서 선택된 지역이 바뀌면 해당 좌표로 지도 이동 (양방향 연동)
  useEffect(() => {
    const map = mapRef.current;
    if (kakaoStatus !== 'ready' || !map || !regionCode) {
      return;
    }

    const selected = ranking?.find((item) => item.regionCode === regionCode);
    if (!selected || selected.latitude === null || selected.longitude === null) {
      return;
    }

    // 주의: panTo(애니메이션) 직후 setLevel을 바로 호출하면 카카오맵 SDK가 팬
    // 애니메이션이 끝나기 전에 끊어버리고 이전 중심 좌표로 되돌아가는 문제가 실제로
    // 발생함(헤드리스 브라우저로 재현 확인). setCenter(즉시 이동)로 경쟁 상태를 없앤다.
    map.setCenter(new window.kakao.maps.LatLng(selected.latitude, selected.longitude));
    map.setLevel(SELECTED_LEVEL);
  }, [regionCode, ranking, kakaoStatus]);

  const rankingList = ranking ?? [];

  return (
    <Layout>
      <Sidebar>
        <SidebarHeader>
          <IndustrySelector />
        </SidebarHeader>
        <RankingHeaderRow>
          <RankingTitle>랭킹</RankingTitle>
          {industryCode && !isLoading && !isError && <RankingCount>{rankingList.length}개 지역</RankingCount>}
        </RankingHeaderRow>
        <RankingScroll>
          {!industryCode && <SidebarEmptyState>업종을 먼저 선택해주세요.</SidebarEmptyState>}
          {industryCode && isLoading && <SidebarEmptyState>랭킹을 불러오는 중...</SidebarEmptyState>}
          {industryCode && isError && (
            <SidebarEmptyState>랭킹을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</SidebarEmptyState>
          )}
          {industryCode && !isLoading && !isError && rankingList.length === 0 && (
            <SidebarEmptyState>아직 집계된 데이터가 없습니다. 배치 작업 완료 후 다시 확인해주세요.</SidebarEmptyState>
          )}
          {industryCode &&
            !isLoading &&
            !isError &&
            rankingList.map((item, index) => (
              <RankingRow
                key={item.regionCode}
                type="button"
                $selected={item.regionCode === regionCode}
                onClick={() => setRegionCode(item.regionCode)}
              >
                <RankingRank>{index + 1}</RankingRank>
                <TierDot
                  $color={getAttractivenessTierColor(item.attractivenessTier, theme)}
                  title={ATTRACTIVENESS_TIER_ICON[item.attractivenessTier]}
                />
                <RankingRegionName>{item.regionName}</RankingRegionName>
                <RankingScore>{item.totalScore}</RankingScore>
              </RankingRow>
            ))}
        </RankingScroll>
      </Sidebar>

      <MapCanvasArea>
        {kakaoStatus === 'error' ? (
          <FallbackMessage>
            지도를 불러올 수 없습니다. VITE_KAKAO_MAP_APP_KEY 설정을 확인해주세요.
          </FallbackMessage>
        ) : (
          <>
            <MapContainer ref={containerRef} />
            <RegionSearchBox industryCode={industryCode} ranking={rankingList} onSelectRegion={setRegionCode} />
            {!industryCode && (
              <EmptyOverlay>
                업종을 선택하면 지역별 점수가 지도에 표시됩니다.
                <br />
                점수는 지역만으로는 계산되지 않고, 지역 × 업종 조합에서만 산출돼요.
              </EmptyOverlay>
            )}
            {industryCode && isLoading && <EmptyOverlay>점수를 불러오는 중...</EmptyOverlay>}
            {industryCode && isError && (
              <EmptyOverlay>지도 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</EmptyOverlay>
            )}
            {industryCode && !isLoading && !isError && rankingList.length === 0 && (
              <EmptyOverlay>아직 집계된 데이터가 없습니다. 배치 작업 완료 후 다시 확인해주세요.</EmptyOverlay>
            )}
            <AttributionBadge>출처: 소상공인시장진흥공단</AttributionBadge>
          </>
        )}

        <SlidePanel $open={Boolean(regionCode && industryCode)} aria-hidden={!regionCode}>
          <RegionIndustryDetailPanel />
        </SlidePanel>
      </MapCanvasArea>
    </Layout>
  );
}
