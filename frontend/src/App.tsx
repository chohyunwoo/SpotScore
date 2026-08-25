import { lazy, Suspense, useState } from 'react';
import styled from 'styled-components';
import { AuthBar } from './components/Auth/AuthBar';
import { MapDashboard } from './components/MapDashboard/MapDashboard';

// 모달류는 처음 화면에 필요 없으므로 지연 로딩해 초기 번들에서 뺀다(named export라 default로 매핑).
const AuthModal = lazy(() =>
  import('./components/Auth/AuthModal').then((m) => ({ default: m.AuthModal })),
);
const CompareView = lazy(() =>
  import('./components/Compare/CompareView').then((m) => ({ default: m.CompareView })),
);

const Page = styled.div`
  /* dvh: 모바일 주소창 영역까지 100vh에 포함돼 하단이 잘리는 문제를 막는다(vh 폴백). */
  height: 100vh;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  padding: ${({ theme }) => theme.spacing.lg};
  gap: ${({ theme }) => theme.spacing.md};

  /* 폰: 고정 높이 셸이 짧은 뷰포트에서 잘려 보이므로, 세로로 흐르며 스크롤되게 한다. */
  @media (max-width: ${({ theme }) => theme.breakpoint.mobile}) {
    height: auto;
    min-height: 100dvh;
    padding: ${({ theme }) => theme.spacing.sm};
  }
`;

const TopBar = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${({ theme }) => theme.spacing.md};

  /* 폰: 긴 제목+로그인 버튼이 한 줄에 안 들어가 가로로 넘치므로 줄바꿈 허용. */
  @media (max-width: ${({ theme }) => theme.breakpoint.mobile}) {
    flex-wrap: wrap;
    gap: ${({ theme }) => theme.spacing.sm};
  }
`;

const AppTitle = styled.h1`
  margin: 0;
  min-width: 0;
  font-size: ${({ theme }) => theme.typography.h2.size};
  font-weight: ${({ theme }) => theme.typography.h2.weight};
  color: ${({ theme }) => theme.colors.textPrimary};

  @media (max-width: ${({ theme }) => theme.breakpoint.mobile}) {
    font-size: ${({ theme }) => theme.typography.h3.size};
  }
`;

const DashboardArea = styled.div`
  flex: 1;
  min-height: 0;

  /* 폰: Page가 세로 스크롤이 되므로 이 영역은 내용(지도+사이드바) 높이만큼 차지한다. */
  @media (max-width: ${({ theme }) => theme.breakpoint.mobile}) {
    flex: none;
  }
`;

function App() {
  const [compareOpen, setCompareOpen] = useState(false);

  return (
    <Page>
      <TopBar>
        <AppTitle>SpotScore — 창업 입지 추천 대시보드</AppTitle>
        <AuthBar onOpenCompare={() => setCompareOpen(true)} />
      </TopBar>
      <DashboardArea>
        <MapDashboard />
      </DashboardArea>

      {compareOpen && (
        <Suspense fallback={null}>
          <CompareView onClose={() => setCompareOpen(false)} />
        </Suspense>
      )}
      <Suspense fallback={null}>
        <AuthModal />
      </Suspense>
    </Page>
  );
}

export default App;
