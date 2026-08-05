import { createContext, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

/**
 * 업종 선택(필수) → 랭킹 리스트/지도(양방향 연동) → 상세 패널 흐름에서
 * 공유되는 선택 상태. React Context API 사용(CLAUDE.md 상태관리 확정 스택).
 */
interface SelectionContextValue {
  industryCode: string | null;
  regionCode: string | null;
  setIndustryCode: (code: string | null) => void;
  setRegionCode: (code: string | null) => void;
}

const SelectionContext = createContext<SelectionContextValue | undefined>(undefined);

export function SelectionProvider({ children }: { children: ReactNode }) {
  const [industryCode, setIndustryCodeState] = useState<string | null>(null);
  const [regionCode, setRegionCode] = useState<string | null>(null);

  const setIndustryCode = (code: string | null) => {
    setIndustryCodeState(code);
    // 업종이 바뀌면 이전 지역 선택(랭킹/상세 대상)은 더 이상 유효하지 않으므로 초기화.
    setRegionCode(null);
  };

  const value = useMemo(
    () => ({ industryCode, regionCode, setIndustryCode, setRegionCode }),
    [industryCode, regionCode],
  );

  return <SelectionContext.Provider value={value}>{children}</SelectionContext.Provider>;
}

export function useSelection(): SelectionContextValue {
  const ctx = useContext(SelectionContext);
  if (!ctx) {
    throw new Error('useSelection must be used within a SelectionProvider');
  }
  return ctx;
}
