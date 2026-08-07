package com.spotscore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * 소상공인시장진흥공단 상가(상권)정보 storeListInDong 응답의 개별 업소 원본 행.
 * STORE_COUNT(지역x업종 집계, 점수 계산 원자료)와 별개로, 지도에 개별 마커를
 * 표시하기 위한 조회 전용 데이터다. bizesId는 상권정보 API가 부여하는 전국 유일
 * 식별자라 PK로 쓰고, 재수집 시 같은 bizesId는 최신 정보로 갱신한다(이력 아님).
 */
@Entity
@Table(name = "store")
public class Store {

    @Id
    @Column(name = "bizes_id", length = 30)
    private String bizesId;

    @Column(name = "bizes_nm", nullable = false, length = 200)
    private String bizesNm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inds_mcls_cd", nullable = false)
    private IndustryCategory industry;

    @Column(name = "inds_mcls_nm", nullable = false, length = 100)
    private String indsMclsNm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code", nullable = false)
    private Region region;

    @Column(name = "lon")
    private Double lon;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    protected Store() {
    }

    public Store(String bizesId, String bizesNm, IndustryCategory industry, String indsMclsNm,
                 Region region, Double lon, Double lat, LocalDate snapshotDate) {
        this.bizesId = bizesId;
        this.bizesNm = bizesNm;
        this.industry = industry;
        this.indsMclsNm = indsMclsNm;
        this.region = region;
        this.lon = lon;
        this.lat = lat;
        this.snapshotDate = snapshotDate;
    }

    public void update(String bizesNm, IndustryCategory industry, String indsMclsNm, Region region,
                        Double lon, Double lat, LocalDate snapshotDate) {
        this.bizesNm = bizesNm;
        this.industry = industry;
        this.indsMclsNm = indsMclsNm;
        this.region = region;
        this.lon = lon;
        this.lat = lat;
        this.snapshotDate = snapshotDate;
    }

    public String getBizesId() {
        return bizesId;
    }

    public String getBizesNm() {
        return bizesNm;
    }

    public IndustryCategory getIndustry() {
        return industry;
    }

    public String getIndsMclsNm() {
        return indsMclsNm;
    }

    public Region getRegion() {
        return region;
    }

    public Double getLon() {
        return lon;
    }

    public Double getLat() {
        return lat;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }
}
