package com.spotscore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 업종별 ageScore 방향성 설정 테이블. CLAUDE.md 확장성 원칙에 따라 업종 코드를
 * 애플리케이션 코드에 나열하지 않고, 대분류 접두어 기준 시딩 로직
 * (IndustryAgeDirectionSeedingService)이 값을 채운다.
 */
@Entity
@Table(name = "industry_age_direction")
public class IndustryAgeDirection {

    @Id
    @Column(name = "industry_code", length = 10)
    private String industryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private AgeDirection direction;

    protected IndustryAgeDirection() {
    }

    public IndustryAgeDirection(String industryCode, AgeDirection direction) {
        this.industryCode = industryCode;
        this.direction = direction;
    }

    public String getIndustryCode() {
        return industryCode;
    }

    public AgeDirection getDirection() {
        return direction;
    }

    public void updateDirection(AgeDirection direction) {
        this.direction = direction;
    }
}
