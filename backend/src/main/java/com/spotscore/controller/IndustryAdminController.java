package com.spotscore.controller;

import com.spotscore.batch.FeaturedIndustrySeedResult;
import com.spotscore.batch.FeaturedIndustrySeedingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 업종 관련 관리용 작업 수동 트리거.
 *
 * TODO: 운영 배포 전 인증 추가 필요 - 현재는 로컬 전용으로 인증 없이 열려 있다.
 */
@Tag(name = "IndustryAdmin", description = "추천 업종(featured) 시딩 등 관리용 작업 수동 트리거")
@RestController
public class IndustryAdminController {

    private static final Logger log = LoggerFactory.getLogger(IndustryAdminController.class);

    private final FeaturedIndustrySeedingService featuredIndustrySeedingService;

    public IndustryAdminController(FeaturedIndustrySeedingService featuredIndustrySeedingService) {
        this.featuredIndustrySeedingService = featuredIndustrySeedingService;
    }

    @Operation(summary = "추천 업종(featured) 시딩",
            description = "spotscore.industry.featured-codes 설정값 기준으로 industry_category.featured를 갱신한다 (idempotent).")
    @PostMapping("/api/v1/admin/industries/seed-featured")
    public FeaturedIndustrySeedResult seedFeatured(HttpServletRequest request) {
        log.info("요청 수신 - endpoint: {}", request.getRequestURI());
        return featuredIndustrySeedingService.seedFeaturedIndustries();
    }
}
