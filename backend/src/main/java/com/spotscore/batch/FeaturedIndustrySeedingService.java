package com.spotscore.batch;

import com.spotscore.config.FeaturedIndustryProperties;
import com.spotscore.domain.IndustryCategory;
import com.spotscore.repository.IndustryCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * industry_category.featured 값 갱신(스키마 변경인 V7 마이그레이션과 분리된 데이터
 * 갱신 로직). spotscore.industry.featured-codes에 있는 코드만 true로 세팅하고,
 * 그 목록에 없는 기존 featured=true 업종은 false로 되돌린다 - 재실행해도 항상
 * "지금 설정값과 일치하는" 상태로 수렴하는 idempotent 동작.
 */
@Service
public class FeaturedIndustrySeedingService {

    private static final Logger log = LoggerFactory.getLogger(FeaturedIndustrySeedingService.class);

    private final IndustryCategoryRepository industryCategoryRepository;
    private final FeaturedIndustryProperties featuredIndustryProperties;

    public FeaturedIndustrySeedingService(IndustryCategoryRepository industryCategoryRepository,
                                           FeaturedIndustryProperties featuredIndustryProperties) {
        this.industryCategoryRepository = industryCategoryRepository;
        this.featuredIndustryProperties = featuredIndustryProperties;
    }

    @Transactional
    public FeaturedIndustrySeedResult seedFeaturedIndustries() {
        var targetCodes = featuredIndustryProperties.featuredCodes();
        log.info("추천 업종 시딩 시작 - 대상 {}건", targetCodes.size());

        int applied = 0;
        int notFound = 0;
        for (String code : targetCodes) {
            IndustryCategory category = industryCategoryRepository.findById(code).orElse(null);
            if (category == null) {
                log.warn("추천 업종 시딩 실패 - industryCode: {} (industry_category에 없는 코드, 확인 필요)", code);
                notFound++;
                continue;
            }
            category.updateFeatured(true);
            applied++;
        }

        int unfeatured = 0;
        for (IndustryCategory category : industryCategoryRepository.findByFeaturedTrue()) {
            if (!targetCodes.contains(category.getIndustryCode())) {
                category.updateFeatured(false);
                unfeatured++;
            }
        }

        log.info("추천 업종 시딩 종료 - 적용 {}건, 코드 없음 {}건, 해제 {}건", applied, notFound, unfeatured);
        return new FeaturedIndustrySeedResult(targetCodes.size(), applied, notFound, unfeatured);
    }
}
