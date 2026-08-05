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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 지역 x 업종 조합별 산출된 종합/브레이크다운 점수 캐시. household_score는 V3
 * 마이그레이션으로 추가되어 대시보드 상세 패널의 3개 브레이크다운
 * (인구 규모/가구 구조/경쟁 밀집도)을 각각 별도 컬럼으로 노출한다.
 */
@Entity
@Table(name = "score_cache", uniqueConstraints = @UniqueConstraint(columnNames = {"region_code", "industry_code"}))
public class ScoreCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code", nullable = false)
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "industry_code", nullable = false)
    private IndustryCategory industry;

    @Column(name = "total_score", nullable = false)
    private BigDecimal totalScore;

    @Column(name = "population_score", nullable = false)
    private BigDecimal populationScore;

    @Column(name = "density_score", nullable = false)
    private BigDecimal densityScore;

    @Column(name = "household_score", nullable = false)
    private BigDecimal householdScore;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    protected ScoreCache() {
    }

    public ScoreCache(Region region, IndustryCategory industry, BigDecimal totalScore,
                       BigDecimal populationScore, BigDecimal householdScore, BigDecimal densityScore,
                       LocalDateTime calculatedAt) {
        this.region = region;
        this.industry = industry;
        this.totalScore = totalScore;
        this.populationScore = populationScore;
        this.householdScore = householdScore;
        this.densityScore = densityScore;
        this.calculatedAt = calculatedAt;
    }

    public void update(BigDecimal totalScore, BigDecimal populationScore, BigDecimal householdScore,
                        BigDecimal densityScore, LocalDateTime calculatedAt) {
        this.totalScore = totalScore;
        this.populationScore = populationScore;
        this.householdScore = householdScore;
        this.densityScore = densityScore;
        this.calculatedAt = calculatedAt;
    }

    public Long getId() {
        return id;
    }

    public Region getRegion() {
        return region;
    }

    public IndustryCategory getIndustry() {
        return industry;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public BigDecimal getPopulationScore() {
        return populationScore;
    }

    public BigDecimal getDensityScore() {
        return densityScore;
    }

    public BigDecimal getHouseholdScore() {
        return householdScore;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }
}
