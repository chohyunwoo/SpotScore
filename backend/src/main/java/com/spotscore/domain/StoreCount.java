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
 * 소상공인시장진흥공단 상가(상권)정보 storeListInDong 배치 적재 결과. 동일 지역 내
 * 업종별 업소 수(경쟁 밀집도 원자료)를 월 1회 스냅샷으로 저장한다.
 */
@Entity
@Table(name = "store_count", uniqueConstraints =
        @UniqueConstraint(columnNames = {"region_code", "industry_code", "snapshot_date"}))
public class StoreCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code", nullable = false)
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "industry_code", nullable = false)
    private IndustryCategory industry;

    @Column(name = "store_count", nullable = false)
    private int storeCount;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    protected StoreCount() {
    }

    public StoreCount(Region region, IndustryCategory industry, int storeCount, LocalDate snapshotDate) {
        this.region = region;
        this.industry = industry;
        this.storeCount = storeCount;
        this.snapshotDate = snapshotDate;
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

    public int getStoreCount() {
        return storeCount;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void updateCount(int storeCount) {
        this.storeCount = storeCount;
    }
}
