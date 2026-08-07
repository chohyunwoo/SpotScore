import styled from 'styled-components';
import { MapDashboard } from './components/MapDashboard/MapDashboard';

const Page = styled.div`
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: ${({ theme }) => theme.spacing.lg};
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
  return (
    <Page>
      <AppTitle>SpotScore — 창업 입지 추천 대시보드</AppTitle>
      <DashboardArea>
        <MapDashboard />
      </DashboardArea>
    </Page>
  );
}

export default App;
