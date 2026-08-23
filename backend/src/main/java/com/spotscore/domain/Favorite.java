package com.spotscore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 사용자가 저장한 관심 "지역 x 업종" 조합(V14 마이그레이션). 점수는 항상 지역x업종
 * 조합에 대해서만 산출되므로(CLAUDE.md), 즐겨찾기도 두 코드의 조합을 단위로 둔다.
 * region/industry는 기존 코드 테이블 FK로 참조하고, (user, region, industry) 조합은
 * DB UNIQUE 제약으로 중복 저장을 막는다.
 */
@Entity
@Table(name = "favorite",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "region_code", "industry_code"}))
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code", nullable = false)
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "industry_code", nullable = false)
    private IndustryCategory industry;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Favorite() {
    }

    public Favorite(AppUser user, Region region, IndustryCategory industry, LocalDateTime createdAt) {
        this.user = user;
        this.region = region;
        this.industry = industry;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Region getRegion() {
        return region;
    }

    public IndustryCategory getIndustry() {
        return industry;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
