package com.spotscore.batch;

import com.spotscore.collector.SgisBoundaryCollector;
import com.spotscore.collector.dto.BoundaryCentroidDto;
import com.spotscore.domain.Region;
import com.spotscore.repository.RegionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * REGION.latitude/longitude를 SGIS 경계 centroid로 채우는 1회성 시딩. 경계는 거의
 * 바뀌지 않으므로 @Scheduled 월간 배치(MonthlyDataCollectionBatchJob)와는 분리하고,
 * 관리용 엔드포인트로만 수동 트리거한다 - 반복 호출해도 이미 좌표가 채워진 지역은
 * 대상에서 빠지므로(RegionRepository.findAllBySgisAdmCdIsNotNullAndLatitudeIsNull)
 * 안전하게 재실행 가능하다.
 */
@Service
public class RegionCoordinateSeedingService {

    private static final Logger log = LoggerFactory.getLogger(RegionCoordinateSeedingService.class);

    private final RegionRepository regionRepository;
    private final SgisBoundaryCollector sgisBoundaryCollector;

    public RegionCoordinateSeedingService(RegionRepository regionRepository, SgisBoundaryCollector sgisBoundaryCollector) {
        this.regionRepository = regionRepository;
        this.sgisBoundaryCollector = sgisBoundaryCollector;
    }

    public RegionCoordinateSeedResult seedMissingCoordinates() {
        Instant startedAt = Instant.now();
        List<Region> targets = regionRepository.findAllBySgisAdmCdIsNotNullAndLatitudeIsNull();
        log.info("좌표 시딩 시작 - 대상 {}건", targets.size());

        int succeeded = 0;
        int failed = 0;
        for (Region region : targets) {
            try {
                if (seedOne(region)) {
                    succeeded++;
                } else {
                    failed++;
                }
            } catch (Exception ex) {
                log.warn("좌표 시딩 실패 - regionCode: {}, sgisAdmCd: {}, 사유: {}",
                        region.getRegionCode(), region.getSgisAdmCd(), ex.getMessage());
                failed++;
            }
        }

        long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
        log.info("좌표 시딩 종료 - 대상 {}건, 성공 {}건, 실패 {}건, 소요시간: {}ms",
                targets.size(), succeeded, failed, elapsedMillis);
        return new RegionCoordinateSeedResult(targets.size(), succeeded, failed, elapsedMillis);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected boolean seedOne(Region region) {
        List<BoundaryCentroidDto> results = sgisBoundaryCollector.collect(region.getSgisAdmCd()).collectList().block();
        if (results == null || results.isEmpty()) {
            log.warn("좌표 시딩 실패 - regionCode: {}, sgisAdmCd: {}, 사유: 경계 응답 없음",
                    region.getRegionCode(), region.getSgisAdmCd());
            return false;
        }

        BoundaryCentroidDto centroid = results.get(0);
        Region managed = regionRepository.findById(region.getRegionCode())
                .orElseThrow(() -> new IllegalStateException("좌표 시딩 중 region 사라짐: " + region.getRegionCode()));
        managed.updateCoordinates(centroid.latitude(), centroid.longitude());
        regionRepository.save(managed);

        log.info("좌표 시딩 성공 - regionCode: {}, latitude: {}, longitude: {}",
                region.getRegionCode(), centroid.latitude(), centroid.longitude());
        return true;
    }
}
