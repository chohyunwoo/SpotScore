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
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: ${({ theme }) => theme.spacing.lg};
  gap: ${({ theme }) => theme.spacing.md};
`;

const TopBar = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${({ theme }) => theme.spacing.md};
`;

const AppTitle = styled.h1`
  margin: 0;
  font-size: ${({ theme }) => theme.typography.h2.size};
  font-weight: ${({ theme }) => theme.typography.h2.weight};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const DashboardArea = styled.div`
  flex: 1;
  min-height: 0;
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
