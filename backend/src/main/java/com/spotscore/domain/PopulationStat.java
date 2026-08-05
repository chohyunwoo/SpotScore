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

/**
 * SGIS stats/population.json 배치 적재 결과. total_family/avg_family_member_count는
 * V2 마이그레이션으로 추가된 가구 구조 컬럼(CLAUDE.md 대시보드 브레이크다운 ②).
 */
@Entity
@Table(name = "population_stat", uniqueConstraints = @UniqueConstraint(columnNames = {"region_code", "year"}))
public class PopulationStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code", nullable = false)
    private Region region;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "total_population")
    private Long totalPopulation;

    @Column(name = "density")
    private BigDecimal density;

    @Column(name = "total_family")
    private Long totalFamily;

    @Column(name = "avg_family_member_count")
    private Double avgFamilyMemberCount;

    protected PopulationStat() {
    }

    public PopulationStat(Region region, int year, Long totalPopulation, BigDecimal density,
                           Long totalFamily, Double avgFamilyMemberCount) {
        this.region = region;
        this.year = year;
        this.totalPopulation = totalPopulation;
        this.density = density;
        this.totalFamily = totalFamily;
        this.avgFamilyMemberCount = avgFamilyMemberCount;
    }

    public Long getId() {
        return id;
    }

    public Region getRegion() {
        return region;
    }

    public int getYear() {
        return year;
    }

    public Long getTotalPopulation() {
        return totalPopulation;
    }

    public BigDecimal getDensity() {
        return density;
    }

    public Long getTotalFamily() {
        return totalFamily;
    }

    public Double getAvgFamilyMemberCount() {
        return avgFamilyMemberCount;
    }

    public void update(Long totalPopulation, BigDecimal density, Long totalFamily, Double avgFamilyMemberCount) {
        this.totalPopulation = totalPopulation;
        this.density = density;
        this.totalFamily = totalFamily;
        this.avgFamilyMemberCount = avgFamilyMemberCount;
    }
}
