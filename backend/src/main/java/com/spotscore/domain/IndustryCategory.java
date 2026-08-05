package com.spotscore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 업종 코드 테이블. 상권정보 API의 업종 코드 체계(indsMclsCd)를 그대로 적재한다 -
 * 표준산업분류와의 불일치 처리 기준은 아직 미확정(CLAUDE.md 참고).
 */
@Entity
@Table(name = "industry_category")
public class IndustryCategory {

    @Id
    @Column(name = "industry_code", length = 10)
    private String industryCode;

    @Column(name = "industry_name", nullable = false, length = 100)
    private String industryName;

    @Column(name = "level", nullable = false, length = 20)
    private String level;

    // V7 추가 - 업종 선택 드롭다운 기본 노출 여부. 실제 업소 수 집계로 정해지는
    // 값이라 코드에 업종 코드를 나열하지 않고 이 컬럼/FeaturedIndustrySeedingService로 관리한다.
    @Column(name = "featured", nullable = false)
    private boolean featured;

    protected IndustryCategory() {
    }

    public IndustryCategory(String industryCode, String industryName, String level) {
        this.industryCode = industryCode;
        this.industryName = industryName;
        this.level = level;
    }

    public String getIndustryCode() {
        return industryCode;
    }

    public String getIndustryName() {
        return industryName;
    }

    public String getLevel() {
        return level;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void updateName(String industryName) {
        this.industryName = industryName;
    }

    public void updateFeatured(boolean featured) {
        this.featured = featured;
    }
}
