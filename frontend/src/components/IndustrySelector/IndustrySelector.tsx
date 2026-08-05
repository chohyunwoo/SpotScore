import { useMemo, useState } from 'react';
import styled from 'styled-components';
import { useIndustries } from '../../api/industries';
import { useSelection } from '../../context/SelectionContext';

const Wrapper = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 320px;
`;

const Label = styled.label`
  font-size: 13px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.textSecondary};
`;

const Select = styled.select`
  padding: 10px 12px;
  border-radius: ${({ theme }) => theme.radius};
  border: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
  font-size: 15px;
  color: ${({ theme }) => theme.colors.textPrimary};

  &:invalid {
    color: ${({ theme }) => theme.colors.textSecondary};
  }
`;

const ToggleButton = styled.button`
  align-self: flex-start;
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: ${({ theme }) => theme.colors.primary};
  cursor: pointer;
  text-decoration: underline;

  &:hover {
    color: ${({ theme }) => theme.colors.textPrimary};
  }
`;

/**
 * 업종 선택 드롭다운. 탐색 흐름상 필수 선택 게이트 —
 * 값이 없으면(빈 문자열) RankingList/MapView/DetailPanel은 안내 문구만 표시.
 *
 * 기본은 추천 업종(featured=true, 30개)만 보여주고, "전체 업종 보기" 토글을
 * 누르면 ?all=true로 전체(75개)를 불러와 "추천 업종"/"전체 업종" 두 섹션으로
 * 나눠 보여준다. 어떤 코드가 추천인지는 여기서 나열하지 않고, featured
 * 목록(기본 호출)과 all 목록을 실제로 대조해서 나눈다 - 하드코딩 금지
 * (CLAUDE.md 확장성 원칙 6.4절).
 */
export function IndustrySelector() {
  const [showAll, setShowAll] = useState(false);
  const { data: featuredIndustries, isLoading: isFeaturedLoading, isError: isFeaturedError } = useIndustries(false);
  const {
    data: allIndustries,
    isLoading: isAllLoading,
    isError: isAllError,
  } = useIndustries(true, { enabled: showAll });
  const { industryCode, setIndustryCode } = useSelection();

  const featured = featuredIndustries ?? [];

  const rest = useMemo(() => {
    const featuredCodes = new Set(featured.map((industry) => industry.industryCode));
    return (allIndustries ?? []).filter((industry) => !featuredCodes.has(industry.industryCode));
  }, [allIndustries, featured]);

  const isLoading = showAll ? isFeaturedLoading || isAllLoading : isFeaturedLoading;
  const isError = showAll ? isFeaturedError || isAllError : isFeaturedError;
  const isEmpty = !isLoading && !isError && featured.length === 0 && rest.length === 0;

  return (
    <Wrapper>
      <Label htmlFor="industry-select">업종 선택 (필수)</Label>
      <Select
        id="industry-select"
        required
        value={industryCode ?? ''}
        disabled={isLoading || isError || isEmpty}
        onChange={(e) => setIndustryCode(e.target.value || null)}
      >
        <option value="" disabled>
          {isLoading
            ? '업종 불러오는 중...'
            : isError
              ? '업종 목록을 불러오지 못했습니다'
              : isEmpty
                ? '등록된 업종이 없습니다 (배치 미실행)'
                : '업종을 선택하세요'}
        </option>
        {showAll ? (
          <>
            <optgroup label="추천 업종">
              {featured.map((industry) => (
                <option key={industry.industryCode} value={industry.industryCode}>
                  {industry.industryName}
                </option>
              ))}
            </optgroup>
            <optgroup label="전체 업종">
              {rest.map((industry) => (
                <option key={industry.industryCode} value={industry.industryCode}>
                  {industry.industryName}
                </option>
              ))}
            </optgroup>
          </>
        ) : (
          featured.map((industry) => (
            <option key={industry.industryCode} value={industry.industryCode}>
              {industry.industryName}
            </option>
          ))
        )}
      </Select>
      <ToggleButton type="button" onClick={() => setShowAll((prev) => !prev)}>
        {showAll ? '추천 업종만 보기' : '전체 업종 보기'}
      </ToggleButton>
    </Wrapper>
  );
}
