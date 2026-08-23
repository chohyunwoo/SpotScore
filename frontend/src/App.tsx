import { useState } from 'react';
import styled from 'styled-components';
import { AuthBar } from './components/Auth/AuthBar';
import { AuthModal } from './components/Auth/AuthModal';
import { CompareView } from './components/Compare/CompareView';
import { MapDashboard } from './components/MapDashboard/MapDashboard';

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

      {compareOpen && <CompareView onClose={() => setCompareOpen(false)} />}
      <AuthModal />
    </Page>
  );
}

export default App;
