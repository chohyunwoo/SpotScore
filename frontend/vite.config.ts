import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 코드 스플리팅은 컴포넌트 단의 React.lazy(동적 import) 경계로만 처리한다.
// recharts/react-markdown은 각각 지연 로딩되는 RegionIndustryDetailPanel/ChatWidget
// 에서만 import되므로 Rollup이 자동으로 별도 async 청크로 분리한다. manualChunks로
// 억지로 벤더를 묶으면 react-dom이 recharts 청크에 합쳐져 초기 로드로 끌려오는
// 역효과가 나서 쓰지 않는다(실측으로 확인).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
});
