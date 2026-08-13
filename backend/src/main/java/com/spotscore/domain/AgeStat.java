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

import java.time.LocalDate;

/**
 * KOSIS(통계청) DT_1B04005N(행정구역(읍면동)별/5세별 주민등록인구) 배치 적재 결과.
 *
 * kosisTotalPopulation은 population_stat.total_population(SGIS 추계인구)과
 * 통계 기준이 다른 별개 수치라 절대 혼용하지 않는다(CLAUDE.md 연령 구성 지표
 * 섹션) - 컬럼도 분리돼 있다.
 */
@Entity
@Table(name = "age_stat", uniqueConstraints = @UniqueConstraint(columnNames = {"region_code", "year"}))
public class AgeStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code", nullable = false)
    private Region region;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "age2039_cnt")
    private Long age2039Cnt;

    @Column(name = "kosis_total_population")
    private Long kosisTotalPopulation;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    protected AgeStat() {
    }

    public AgeStat(Region region, int year, Long age2039Cnt, Long kosisTotalPopulation, LocalDate snapshotDate) {
        this.region = region;
        this.year = year;
        this.age2039Cnt = age2039Cnt;
        this.kosisTotalPopulation = kosisTotalPopulation;
        this.snapshotDate = snapshotDate;
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

    public Long getAge2039Cnt() {
        return age2039Cnt;
    }

    public Long getKosisTotalPopulation() {
        return kosisTotalPopulation;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void update(Long age2039Cnt, Long kosisTotalPopulation, LocalDate snapshotDate) {
        this.age2039Cnt = age2039Cnt;
        this.kosisTotalPopulation = kosisTotalPopulation;
        this.snapshotDate = snapshotDate;
    }
}
