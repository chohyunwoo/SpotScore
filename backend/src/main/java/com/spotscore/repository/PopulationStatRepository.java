package com.spotscore.repository;

import com.spotscore.domain.PopulationStat;
import com.spotscore.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PopulationStatRepository extends JpaRepository<PopulationStat, Long> {

    Optional<PopulationStat> findByRegionAndYear(Region region, int year);

    List<PopulationStat> findAllByYear(int year);

    Optional<PopulationStat> findTopByRegionOrderByYearDesc(Region region);

    // region_code 교정 시 옛 코드 밑에 있던 population_stat을 지운다 - REGION FK가
    // NO ACTION이라 부모 row를 지우려면 먼저 자식을 치워야 한다. 실제 통계값은
    // 다음 배치가 올바른 새 코드로 다시 채운다(RegionCrosswalkRebuildService 참고).
    void deleteByRegion_RegionCode(String regionCode);
}
