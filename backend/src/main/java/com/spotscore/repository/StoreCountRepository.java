package com.spotscore.repository;

import com.spotscore.domain.IndustryCategory;
import com.spotscore.domain.Region;
import com.spotscore.domain.StoreCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StoreCountRepository extends JpaRepository<StoreCount, Long> {

    Optional<StoreCount> findByRegionAndIndustryAndSnapshotDate(Region region, IndustryCategory industry, LocalDate snapshotDate);

    List<StoreCount> findAllBySnapshotDate(LocalDate snapshotDate);

    Optional<StoreCount> findTopByRegionAndIndustryOrderBySnapshotDateDesc(Region region, IndustryCategory industry);

    // region_code 교정 시 옛(틀린) 코드 밑에 남은 store_count를 정리한다 - 진단상
    // 틀린 코드는 NODATA_ERROR라 실제로는 0건이지만, 재실행 가능한 재구축 로직으로
    // 만들기 위해 방어적으로 둔다(RegionCrosswalkRebuildService 참고).
    void deleteByRegion_RegionCode(String regionCode);
}
