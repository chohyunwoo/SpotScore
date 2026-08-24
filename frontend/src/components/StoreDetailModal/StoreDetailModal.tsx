import { useEffect, useRef } from 'react';
import styled from 'styled-components';
import { useStorePlaceLink } from '../../api/stores';
import type { StoreItem } from '../../types/domain';
import { useKakaoLoader } from '../MapDashboard/useKakaoLoader';

/**
 * 가게 상세 모달. 우리 DB가 가진 정보(가게명·업종·지역·좌표)만 보여주고, 주소·전화·
 * 리뷰 같은 풍부한 정보는 "카카오맵에서 보기" 링크로 위임한다(상권정보 API/우리
 * 저장 범위엔 그 데이터가 없기 때문). 라우터가 없는 단일 페이지 앱이라 별도 라우트
 * 대신 AuthModal과 동일한 백드롭 모달 패턴을 재사용한다(이슈 #34).
 */

const Backdrop = styled.div`
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.spacing.lg};
  background: rgba(16, 24, 40, 0.45);
`;

const Card = styled.div`
  width: 100%;
  max-width: 420px;
  background: ${({ theme }) => theme.colors.surface};
  border-radius: ${({ theme }) => theme.radius.lg};
  box-shadow: ${({ theme }) => theme.shadow.panel};
  padding: ${({ theme }) => theme.spacing.xl};
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.md};
`;

const CloseButton = styled.button`
  align-self: flex-end;
  margin-top: -${({ theme }) => theme.spacing.sm};
  border: none;
  background: none;
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
  color: ${({ theme }) => theme.colors.textTertiary};

  &:hover {
    color: ${({ theme }) => theme.colors.textPrimary};
  }
`;

const StoreName = styled.h2`
  margin: 0;
  font-size: ${({ theme }) => theme.typography.h2.size};
  font-weight: ${({ theme }) => theme.typography.h2.weight};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const InfoList = styled.dl`
  margin: 0;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: ${({ theme }) => `${theme.spacing.xs} ${theme.spacing.md}`};

  dt {
    color: ${({ theme }) => theme.colors.textSecondary};
    font-size: ${({ theme }) => theme.typography.bodySmall.size};
  }
  dd {
    margin: 0;
    color: ${({ theme }) => theme.colors.textPrimary};
    font-size: ${({ theme }) => theme.typography.bodySmall.size};
  }
`;

const MiniMap = styled.div`
  width: 100%;
  height: 180px;
  border-radius: ${({ theme }) => theme.radius.sm};
  border: 1px solid ${({ theme }) => theme.colors.border};
  overflow: hidden;
`;

const NoMap = styled.div`
  width: 100%;
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: ${({ theme }) => theme.radius.sm};
  border: 1px dashed ${({ theme }) => theme.colors.border};
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  text-align: center;
  padding: ${({ theme }) => theme.spacing.md};
`;

const KakaoLink = styled.a`
  display: block;
  text-align: center;
  padding: ${({ theme }) => theme.spacing.sm};
  border-radius: ${({ theme }) => theme.radius.sm};
  background: ${({ theme }) => theme.colors.accent};
  color: ${({ theme }) => theme.colors.onAccent};
  font-size: ${({ theme }) => theme.typography.body.size};
  font-weight: ${({ theme }) => theme.typography.weight.semibold};
  text-decoration: none;

  &:hover {
    background: ${({ theme }) => theme.colors.accentHover};
  }
`;

const SourceNote = styled.p`
  margin: 0;
  font-size: ${({ theme }) => theme.typography.caption.size};
  color: ${({ theme }) => theme.colors.textSecondary};
  line-height: ${({ theme }) => theme.typography.caption.lineHeight};
`;

interface StoreDetailModalProps {
  store: StoreItem;
  regionName: string;
  industryName: string;
  onClose: () => void;
}

/** 이름 검색 폴백 URL - 동 이름을 함께 넣어 동명이인 상호의 오검색을 줄인다. */
function buildSearchFallbackUrl(name: string, regionName: string): string {
  return `https://map.kakao.com/?q=${encodeURIComponent(`${name} ${regionName}`)}`;
}

export function StoreDetailModal({ store, regionName, industryName, onClose }: StoreDetailModalProps) {
  const kakaoStatus = useKakaoLoader();
  const miniMapRef = useRef<HTMLDivElement>(null);
  const hasCoordinate = store.lat !== null && store.lon !== null;

  // Kakao Local 검색으로 실제 등록 장소 상세(place_url)를 받아온다. 없으면(키 미설정/결과
  // 없음/조회 중) 이름 검색 링크로 폴백해, 링크는 항상 유효하게 유지한다.
  const { data: placeLink, isLoading: placeLinkLoading } = useStorePlaceLink(store.bizesId);
  const kakaoUrl = placeLink?.placeUrl ?? buildSearchFallbackUrl(store.bizesNm, regionName);

  // Esc로 닫기 - AuthModal은 백드롭 클릭만 지원하지만, 여기선 키보드 접근성도 더한다.
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  // 좌표가 있고 SDK가 준비됐을 때만 미니맵을 그린다. 전역 SDK를 재사용하므로
  // (useKakaoLoader가 1회 로드) 새 Map 인스턴스만 만들면 된다.
  useEffect(() => {
    if (kakaoStatus !== 'ready' || !hasCoordinate || !miniMapRef.current) {
      return;
    }
    const position = new window.kakao.maps.LatLng(store.lat as number, store.lon as number);
    const map = new window.kakao.maps.Map(miniMapRef.current, { center: position, level: 3 });
    const marker = new window.kakao.maps.Marker({ position });
    marker.setMap(map);
  }, [kakaoStatus, hasCoordinate, store.lat, store.lon]);

  return (
    <Backdrop onClick={onClose}>
      <Card onClick={(event) => event.stopPropagation()}>
        <CloseButton type="button" onClick={onClose} aria-label="닫기">
          ×
        </CloseButton>
        <StoreName>{store.bizesNm}</StoreName>
        <InfoList>
          <dt>업종</dt>
          <dd>{industryName}</dd>
          <dt>지역</dt>
          <dd>{regionName}</dd>
        </InfoList>
        {hasCoordinate ? (
          <MiniMap ref={miniMapRef} />
        ) : (
          <NoMap>이 가게는 좌표 정보가 없어 지도를 표시할 수 없어요.</NoMap>
        )}
        <KakaoLink href={kakaoUrl} target="_blank" rel="noopener noreferrer">
          {placeLinkLoading ? '카카오맵 링크 준비 중…' : '카카오맵에서 보기 ↗'}
        </KakaoLink>
        <SourceNote>
          주소·전화·영업시간 등 상세 정보는 카카오맵에서 확인하세요. (가게 데이터 출처: 소상공인시장진흥공단
          상가정보)
        </SourceNote>
      </Card>
    </Backdrop>
  );
}
