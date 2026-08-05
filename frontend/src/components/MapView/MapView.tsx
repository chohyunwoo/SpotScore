import { useEffect, useRef } from 'react';
import styled from 'styled-components';
import { useRanking } from '../../api/scores';
import { useSelection } from '../../context/SelectionContext';
import type { RankingItem } from '../../types/domain';
import { useKakaoLoader } from './useKakaoLoader';

// 지도에 표시할 데이터가 아직 없을 때만 쓰는 기본 중심 좌표(대한민국 국토 중앙 근사치).
// 특정 지역명을 코드에 고정하는 것이 아니라, 좌표 데이터가 없을 때의 렌더링 fallback일 뿐임.
const DEFAULT_CENTER = { latitude: 36.5, longitude: 127.8 };
const DEFAULT_LEVEL = 13;
const SELECTED_LEVEL = 5;

const Panel = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radius};
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 480px;
`;

const Header = styled.div`
  padding: 14px 16px;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  font-weight: 700;
`;

const MapWrapper = styled.div`
  flex: 1;
  min-height: 420px;
  position: relative;
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
  padding: 24px;
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
  background: ${({ theme }) => theme.colors.surface};
  opacity: 0.92;
  pointer-events: none;
`;

const FallbackMessage = styled.div`
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
`;

export function MapView() {
  const { industryCode, regionCode, setRegionCode } = useSelection();
  const { data: ranking, isLoading, isError } = useRanking(industryCode);
  const kakaoStatus = useKakaoLoader();

  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<kakao.maps.Map | null>(null);
  const markersRef = useRef<Map<string, kakao.maps.Marker>>(new Map());

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

  // 랭킹 데이터가 바뀔 때마다 마커 다시 그리기.
  // RankingItem.latitude/longitude는 V6 마이그레이션(REGION 좌표 컬럼) +
  // RegionCoordinateSeedingService 1회성 시딩으로 채워짐 — 시딩 전 지역은 null일
  // 수 있으므로 그런 지역만 개별적으로 마커 생성을 건너뛴다(전체를 막지 않음).
  useEffect(() => {
    const map = mapRef.current;
    if (kakaoStatus !== 'ready' || !map) {
      return;
    }

    markersRef.current.forEach((marker) => marker.setMap(null));
    markersRef.current.clear();

    (ranking ?? []).forEach((item: RankingItem) => {
      const { latitude, longitude } = item;
      if (latitude === null || longitude === null) {
        console.warn('[MapView] 좌표 시딩 전(null)이라 마커를 표시하지 못함', item.regionCode);
        return;
      }

      const marker = new window.kakao.maps.Marker({
        position: new window.kakao.maps.LatLng(latitude, longitude),
        map,
        title: item.regionName,
      });

      window.kakao.maps.event.addListener(marker, 'click', () => {
        setRegionCode(item.regionCode);
      });

      markersRef.current.set(item.regionCode, marker);
    });
  }, [ranking, kakaoStatus, setRegionCode]);

  // 랭킹 리스트에서 선택된 지역이 바뀌면 해당 마커로 지도 이동 (양방향 연동)
  useEffect(() => {
    const map = mapRef.current;
    if (kakaoStatus !== 'ready' || !map || !regionCode) {
      return;
    }

    const marker = markersRef.current.get(regionCode);
    if (!marker) {
      return;
    }

    // 주의: panTo(애니메이션)를 호출한 직후 setLevel을 바로 호출하면 카카오맵 SDK가
    // 팬 애니메이션이 끝나기 전에 끊어버리고 이전 중심 좌표로 되돌아가는 문제가 실제로
    // 발생함(헤드리스 브라우저로 재현 확인). setCenter(즉시 이동)로 바꿔 경쟁 상태를 없앤다.
    map.setCenter(marker.getPosition());
    map.setLevel(SELECTED_LEVEL);
  }, [regionCode, kakaoStatus]);

  return (
    <Panel>
      <Header>지도</Header>
      {kakaoStatus === 'error' ? (
        <FallbackMessage>
          지도를 불러올 수 없습니다. VITE_KAKAO_MAP_APP_KEY 설정을 확인해주세요.
        </FallbackMessage>
      ) : (
        <MapWrapper>
          <MapContainer ref={containerRef} />
          {!industryCode && <EmptyOverlay>업종을 먼저 선택해주세요.</EmptyOverlay>}
          {industryCode && isLoading && <EmptyOverlay>지도 데이터를 불러오는 중...</EmptyOverlay>}
          {industryCode && isError && (
            <EmptyOverlay>지도 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</EmptyOverlay>
          )}
          {industryCode && !isLoading && !isError && ranking?.length === 0 && (
            <EmptyOverlay>아직 집계된 데이터가 없습니다. 배치 작업 완료 후 다시 확인해주세요.</EmptyOverlay>
          )}
        </MapWrapper>
      )}
    </Panel>
  );
}
