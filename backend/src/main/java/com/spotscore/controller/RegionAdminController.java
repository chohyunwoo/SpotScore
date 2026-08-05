package com.spotscore.controller;

import com.spotscore.batch.RegionCoordinateSeedResult;
import com.spotscore.batch.RegionCoordinateSeedingService;
import com.spotscore.discovery.RegionCrosswalkRebuildService;
import com.spotscore.discovery.RegionCrosswalkReport;
import com.spotscore.discovery.SeoulDiscoveryReport;
import com.spotscore.discovery.SeoulRegionDiscoveryService;
import com.spotscore.dto.RegionCoordinateSeedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지역 좌표 시딩 및 서울 전체 매핑 발견 등 관리용 작업 수동 트리거.
 *
 * TODO: 운영 배포 전 인증 추가 필요 - 현재는 로컬 전용으로 인증 없이 열려 있다.
 */
@Tag(name = "RegionAdmin", description = "지역 좌표 시딩/서울 전체 매핑 발견 등 관리용 작업 수동 트리거")
@RestController
public class RegionAdminController {

    private static final Logger log = LoggerFactory.getLogger(RegionAdminController.class);

    private final RegionCoordinateSeedingService regionCoordinateSeedingService;
    private final SeoulRegionDiscoveryService seoulRegionDiscoveryService;
    private final RegionCrosswalkRebuildService regionCrosswalkRebuildService;

    public RegionAdminController(RegionCoordinateSeedingService regionCoordinateSeedingService,
                                  SeoulRegionDiscoveryService seoulRegionDiscoveryService,
                                  RegionCrosswalkRebuildService regionCrosswalkRebuildService) {
        this.regionCoordinateSeedingService = regionCoordinateSeedingService;
        this.seoulRegionDiscoveryService = seoulRegionDiscoveryService;
        this.regionCrosswalkRebuildService = regionCrosswalkRebuildService;
    }

    @Operation(summary = "지역 좌표 1회성 시딩",
            description = "latitude가 비어있는 지역만 SGIS 경계 API로 centroid를 계산해 채운다 (idempotent).")
    @PostMapping("/api/v1/admin/regions/seed-coordinates")
    public RegionCoordinateSeedResponse seedCoordinates(HttpServletRequest request) {
        log.info("요청 수신 - endpoint: {}", request.getRequestURI());
        RegionCoordinateSeedResult result = regionCoordinateSeedingService.seedMissingCoordinates();
        return RegionCoordinateSeedResponse.from(result);
    }

    @Operation(summary = "서울 전체 행정동 발견 + adm_cd/adongCd 매핑 검증(임시 리포트용 엔드포인트)",
            description = "SGIS 하위 행정구역 목록과 상권정보 시군구코드 표본을 대조해 서울 전체 매핑 실패율을 집계하고, " +
                    "성립하는 조합은 REGION에 반영한다. 좌표 시딩은 별도로 /seed-coordinates를 다시 호출할 것.")
    @PostMapping("/api/v1/admin/regions/discover-seoul")
    public SeoulDiscoveryReport discoverSeoulRegions(HttpServletRequest request) {
        log.info("요청 수신 - endpoint: {}", request.getRequestURI());
        return seoulRegionDiscoveryService.discoverAndValidateSeoulRegions();
    }

    @Operation(summary = "REGION 코드 크로스워크 재구축",
            description = "25개 구를 divId=signguCd로 전체 페이징 조회해 상권정보가 실제로 아는 adongCd/adongNm 전체를 얻고, " +
                    "SGIS 동 이름과 대조해 REGION.region_code를 실제 값으로 교정·추가한다. 매칭 실패 지역은 임의로 채우지 않고 리포트에만 남긴다.")
    @PostMapping("/api/v1/admin/regions/rebuild-crosswalk")
    public RegionCrosswalkReport rebuildCrosswalk(HttpServletRequest request) {
        log.info("요청 수신 - endpoint: {}", request.getRequestURI());
        return regionCrosswalkRebuildService.rebuild();
    }
}
