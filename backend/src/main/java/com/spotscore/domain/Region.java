package com.spotscore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 행정구역 코드 테이블. CLAUDE.md 확장성 원칙에 따라 전국 코드를 적재하고,
 * "서울만" 제약은 조회 조건(WHERE)/설정값으로만 처리한다 - 이 테이블/코드에
 * 지역명을 하드코딩하지 않는다.
 *
 * region_code는 상권정보 API의 행정표준코드(adongCd)를 기준으로 삼는다 - 프론트/
 * 타 공공데이터와의 연동 호환성이 더 높기 때문. SGIS는 같은 지역이라도 자체
 * 시군구 번호체계(adm_cd)를 쓰므로(V5 마이그레이션 참고, 실제 대조로 확인됨)
 * 그 값은 sgisAdmCd에 별도 보관한다.
 */
@Entity
@Table(name = "region")
public class Region {

    @Id
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "region_name", nullable = false, length = 100)
    private String regionName;

    @Column(name = "level", nullable = false, length = 20)
    private String level;

    @Column(name = "sgis_adm_cd", length = 10)
    private String sgisAdmCd;

    // V6 추가 - SGIS boundary/hadmarea.geojson centroid를 1회성으로 계산해 채운다
    // (RegionCoordinateSeedingService). 시딩 전에는 null.
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    protected Region() {
    }

    public Region(String regionCode, String regionName, String level, String sgisAdmCd) {
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.level = level;
        this.sgisAdmCd = sgisAdmCd;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getRegionName() {
        return regionName;
    }

    public String getLevel() {
        return level;
    }

    public String getSgisAdmCd() {
        return sgisAdmCd;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void updateName(String regionName) {
        this.regionName = regionName;
    }

    public void updateSgisAdmCd(String sgisAdmCd) {
        this.sgisAdmCd = sgisAdmCd;
    }

    public void updateCoordinates(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
