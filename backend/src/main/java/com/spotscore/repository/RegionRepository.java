package com.spotscore.repository;

import com.spotscore.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, String> {

    // 좌표 시딩 대상 선정 - latitude가 이미 채워진 지역은 다시 호출하지 않는다
    // (idempotent). sgisAdmCd가 없는 지역은 애초에 SGIS 경계 조회가 불가능하므로 제외.
    List<Region> findAllBySgisAdmCdIsNotNullAndLatitudeIsNull();

    // sgisAdmCd는 region_code(=상권정보 adongCd 추정치)와 달리 SGIS 쪽 코드라
    // 바뀌지 않는다 - RegionCrosswalkRebuildService가 "이 SGIS 동이 이미 REGION에
    // 반영돼 있는지"를 이름이 아니라 이 안정적인 키로 찾을 때 쓴다.
    Optional<Region> findBySgisAdmCd(String sgisAdmCd);
}
