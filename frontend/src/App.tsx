import styled from 'styled-components';
import { DetailPanel } from './components/DetailPanel/DetailPanel';
import { IndustrySelector } from './components/IndustrySelector/IndustrySelector';
import { MapView } from './components/MapView/MapView';
import { RankingList } from './components/RankingList/RankingList';

const Page = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const AppTitle = styled.h1`
  font-size: 22px;
  margin: 0;
`;

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(280px, 380px) 1fr;
  gap: 16px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
`;

function App() {
  return (
    <Page>
      <AppTitle>SpotScore — 창업 입지 추천 대시보드</AppTitle>
      <IndustrySelector />
      <MainGrid>
        <RankingList />
        <MapView />
      </MainGrid>
      <DetailPanel />
    </Page>
  );
}

export default App;
