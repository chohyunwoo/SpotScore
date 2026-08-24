import styled from 'styled-components';
import type { StoreItem } from '../../types/domain';

/**
 * 지도 위에 떠 있는 개별 가게 목록 패널(이슈 #34, C안). 상세 패널(오른쪽 슬라이드,
 * 440px)이 열린 상태에서만 보이므로, 그 패널과 겹치지 않게 패널 왼쪽 가장자리에
 * 붙여 "지도의 오른쪽 상단"에 띄운다. 항목 hover는 지도 마커 강조(onHoverStore)와
 * 연동되고, 클릭은 가게 상세 모달(onSelectStore)을 연다.
 */

// 상세 패널 폭(MapDashboard.DETAIL_PANEL_WIDTH=440px) + 여백. 목록을 그 왼쪽에 둔다.
const PANEL_RIGHT_OFFSET = '456px';

const Root = styled.div`
  position: absolute;
  top: 16px;
  right: ${PANEL_RIGHT_OFFSET};
  z-index: 6;
  width: 240px;
  max-width: calc(100% - ${PANEL_RIGHT_OFFSET} - 16px);
  max-height: calc(100% - 32px);
  display: flex;
  flex-direction: column;
  background: ${({ theme }) => theme.colors.surface};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radius.md};
  box-shadow: ${({ theme }) => theme.shadow.panel};
  overflow: hidden;

  /* 태블릿 이하에서는 슬라이드 패널이 넓어져 겹칠 수 있어 좌상단으로 옮긴다. */
  @media (max-width: ${({ theme }) => theme.breakpoint.tablet}) {
    right: auto;
    left: 16px;
    max-width: calc(100% - 32px);
  }
`;

const Header = styled.div`
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  font-size: ${({ theme }) => theme.typography.h3.size};
  font-weight: ${({ theme }) => theme.typography.h3.weight};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const Count = styled.span`
  margin-left: ${({ theme }) => theme.spacing.xs};
  font-size: ${({ theme }) => theme.typography.caption.size};
  font-weight: ${({ theme }) => theme.typography.weight.regular};
  color: ${({ theme }) => theme.colors.textSecondary};
`;

const Scroll = styled.div`
  overflow-y: auto;
`;

const Row = styled.button`
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

const Name = styled.span`
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const Chevron = styled.span`
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: ${({ theme }) => theme.typography.caption.size};
`;

const Empty = styled.div`
  padding: ${({ theme }) => theme.spacing.md};
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
`;

interface StoreListPanelProps {
  stores: StoreItem[] | undefined;
  isLoading: boolean;
  onHoverStore: (bizesId: string | null) => void;
  onSelectStore: (store: StoreItem) => void;
}

export function StoreListPanel({ stores, isLoading, onHoverStore, onSelectStore }: StoreListPanelProps) {
  return (
    <Root aria-label="가게 목록">
      <Header>
        가게 목록
        {!isLoading && stores != null && <Count>({stores.length}곳)</Count>}
      </Header>
      {isLoading ? (
        <Empty>가게 목록을 불러오는 중...</Empty>
      ) : !stores || stores.length === 0 ? (
        <Empty>표시할 가게가 없어요.</Empty>
      ) : (
        <Scroll>
          {stores.map((store) => (
            <Row
              key={store.bizesId}
              type="button"
              onMouseEnter={() => onHoverStore(store.bizesId)}
              onMouseLeave={() => onHoverStore(null)}
              onFocus={() => onHoverStore(store.bizesId)}
              onBlur={() => onHoverStore(null)}
              onClick={() => onSelectStore(store)}
            >
              <Name>{store.bizesNm}</Name>
              <Chevron>상세 ›</Chevron>
            </Row>
          ))}
        </Scroll>
      )}
    </Root>
  );
}
