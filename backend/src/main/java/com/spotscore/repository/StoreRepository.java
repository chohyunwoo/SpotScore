package com.spotscore.repository;

import com.spotscore.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreRepository extends JpaRepository<Store, String> {

    // GET /api/v1/stores?regionCode=&industryCode= - 상세 패널에서만 호출되는
    // 지역x업종 개별 업소 목록 조회(idx_store_region_industry 인덱스 사용).
    List<Store> findByRegion_RegionCodeAndIndustry_IndustryCode(String regionCode, String industryCode);

    // region_code 교정 시 옛(틀린) 코드 밑에 남은 store를 정리한다 - StoreCountRepository/
    // ScoreCacheRepository와 동일한 이유의 방어적 처리(RegionCrosswalkRebuildService 참고).
    void deleteByRegion_RegionCode(String regionCode);
}
