package com.spotscore.query;

import com.spotscore.domain.IndustryCategory;
import com.spotscore.dto.IndustryResponse;
import com.spotscore.repository.IndustryCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 업종 목록 조회. GET /api/v1/industries는 프론트가 지역 선택 이전에 렌더링하는
 * 업종 선택 UI에 쓰이므로(CLAUDE.md 탐색 흐름: 업종 선택 필수), 업종 코드를
 * 하드코딩하지 않고 industry_category 테이블을 그대로 조회해서 내려준다.
 *
 * 기본값은 featured=true(추천 업종, FeaturedIndustrySeedingService가 채움)만
 * 내려주고, includeAll=true일 때만 전체를 내려준다 - 75개를 코드로 숨기는 게
 * 아니라 DB 플래그로 필터링한다(CLAUDE.md 확장성 원칙 2).
 */
@Service
public class IndustryQueryService {

    private final IndustryCategoryRepository industryCategoryRepository;

    public IndustryQueryService(IndustryCategoryRepository industryCategoryRepository) {
        this.industryCategoryRepository = industryCategoryRepository;
    }

    public List<IndustryResponse> getIndustries(boolean includeAll) {
        List<IndustryCategory> categories = includeAll
                ? industryCategoryRepository.findAll()
                : industryCategoryRepository.findByFeaturedTrue();
        return categories.stream()
                .sorted(Comparator.comparing(IndustryCategory::getIndustryCode))
                .map(IndustryResponse::from)
                .toList();
    }
}
