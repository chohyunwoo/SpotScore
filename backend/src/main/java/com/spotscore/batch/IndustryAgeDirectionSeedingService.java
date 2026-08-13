package com.spotscore.batch;

import com.spotscore.config.IndustryAgeDirectionProperties;
import com.spotscore.domain.AgeDirection;
import com.spotscore.domain.IndustryAgeDirection;
import com.spotscore.domain.IndustryCategory;
import com.spotscore.repository.IndustryAgeDirectionRepository;
import com.spotscore.repository.IndustryCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * industry_age_direction 값 갱신(스키마 변경인 V10 마이그레이션과 분리된 데이터
 * 시딩 로직 - FeaturedIndustrySeedingService와 동일한 패턴). 대분류 접두어
 * (spotscore.industry.age-direction.positive-prefixes/negative-prefixes)로
 * 판정하고, 어느 목록에도 없으면 NEUTRAL로 시딩한다 - industry_category 전체를
 * 대상으로 매번 다시 계산하는 idempotent 동작이라, 재실행해도 항상 현재
 * 설정값과 일치하는 상태로 수렴한다.
 */
@Service
public class IndustryAgeDirectionSeedingService {

    private static final Logger log = LoggerFactory.getLogger(IndustryAgeDirectionSeedingService.class);

    private final IndustryCategoryRepository industryCategoryRepository;
    private final IndustryAgeDirectionRepository industryAgeDirectionRepository;
    private final IndustryAgeDirectionProperties properties;

    public IndustryAgeDirectionSeedingService(IndustryCategoryRepository industryCategoryRepository,
                                               IndustryAgeDirectionRepository industryAgeDirectionRepository,
                                               IndustryAgeDirectionProperties properties) {
        this.industryCategoryRepository = industryCategoryRepository;
        this.industryAgeDirectionRepository = industryAgeDirectionRepository;
        this.properties = properties;
    }

    @Transactional
    public IndustryAgeDirectionSeedResult seedAgeDirections() {
        List<IndustryCategory> allIndustries = industryCategoryRepository.findAll();
        log.info("업종별 연령 방향성 시딩 시작 - 대상 {}건", allIndustries.size());

        int positiveApplied = 0;
        int negativeApplied = 0;
        int neutralApplied = 0;
        for (IndustryCategory industry : allIndustries) {
            AgeDirection direction = resolveDirection(industry.getIndustryCode());
            industryAgeDirectionRepository.findById(industry.getIndustryCode())
                    .ifPresentOrElse(
                            existing -> existing.updateDirection(direction),
                            () -> industryAgeDirectionRepository.save(
                                    new IndustryAgeDirection(industry.getIndustryCode(), direction)));
            switch (direction) {
                case POSITIVE -> positiveApplied++;
                case NEGATIVE -> negativeApplied++;
                case NEUTRAL -> neutralApplied++;
            }
        }

        log.info("업종별 연령 방향성 시딩 종료 - POSITIVE {}건, NEGATIVE {}건, NEUTRAL {}건",
                positiveApplied, negativeApplied, neutralApplied);
        return new IndustryAgeDirectionSeedResult(allIndustries.size(), positiveApplied, negativeApplied, neutralApplied);
    }

    private AgeDirection resolveDirection(String industryCode) {
        if (properties.positivePrefixes().stream().anyMatch(industryCode::startsWith)) {
            return AgeDirection.POSITIVE;
        }
        if (properties.negativePrefixes().stream().anyMatch(industryCode::startsWith)) {
            return AgeDirection.NEGATIVE;
        }
        return AgeDirection.NEUTRAL;
    }
}
