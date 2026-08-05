package com.spotscore.discovery;

import com.spotscore.domain.Region;
import com.spotscore.repository.PopulationStatRepository;
import com.spotscore.repository.RegionRepository;
import com.spotscore.repository.ScoreCacheRepository;
import com.spotscore.repository.StoreCountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * REGION 1건에 대한 실제 교정/추가를 별도 빈으로 분리했다 - RegionCrosswalkRebuildService가
 * 자기 자신의 메서드를 직접 호출(self-invocation)하면 @Transactional이 Spring 프록시를
 * 거치지 않아 무시된다(실제로 겪은 문제: "No EntityManager with actual transaction
 * available for current thread" 오류로 확인됨). 별도 빈으로 두면 다른 빈을 통한 호출이라
 * 프록시가 정상 적용된다.
 */
@Service
public class RegionMappingCorrector {

    private static final Logger log = LoggerFactory.getLogger(RegionMappingCorrector.class);

    private final RegionRepository regionRepository;
    private final PopulationStatRepository populationStatRepository;
    private final StoreCountRepository storeCountRepository;
    private final ScoreCacheRepository scoreCacheRepository;

    public RegionMappingCorrector(RegionRepository regionRepository,
                                   PopulationStatRepository populationStatRepository,
                                   StoreCountRepository storeCountRepository,
                                   ScoreCacheRepository scoreCacheRepository) {
        this.regionRepository = regionRepository;
        this.populationStatRepository = populationStatRepository;
        this.storeCountRepository = storeCountRepository;
        this.scoreCacheRepository = scoreCacheRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApplyResult apply(String sgisAdmCd, String dongName, String realAdongCd) {
        Region existing = regionRepository.findBySgisAdmCd(sgisAdmCd).orElse(null);
        if (existing != null && existing.getRegionCode().equals(realAdongCd)) {
            return ApplyResult.of(ApplyOutcome.ALREADY_CORRECT);
        }

        Region occupying = regionRepository.findById(realAdongCd).orElse(null);
        if (occupying != null && !occupying.getSgisAdmCd().equals(sgisAdmCd)) {
            log.warn("REGION 코드 충돌 - realAdongCd: {}가 이미 다른 sgisAdmCd({})로 점유돼 있음, sgisAdmCd: {}({})는 보류 - 수동 확인 필요",
                    realAdongCd, occupying.getSgisAdmCd(), sgisAdmCd, dongName);
            return ApplyResult.conflict(new RegionCrosswalkReport.ConflictEntry(
                    sgisAdmCd, dongName, realAdongCd, occupying.getSgisAdmCd()));
        }

        Double preservedLatitude = existing == null ? null : existing.getLatitude();
        Double preservedLongitude = existing == null ? null : existing.getLongitude();
        boolean wasNew = existing == null;

        if (existing != null) {
            String oldCode = existing.getRegionCode();
            populationStatRepository.deleteByRegion_RegionCode(oldCode);
            storeCountRepository.deleteByRegion_RegionCode(oldCode);
            scoreCacheRepository.deleteByRegion_RegionCode(oldCode);
            regionRepository.delete(existing);
            log.info("REGION 코드 교정 - sgisAdmCd: {}, dongName: {}, {} -> {}", sgisAdmCd, dongName, oldCode, realAdongCd);
        }

        Region created = new Region(realAdongCd, dongName, "ADONG", sgisAdmCd);
        if (preservedLatitude != null && preservedLongitude != null) {
            created.updateCoordinates(preservedLatitude, preservedLongitude);
        }
        regionRepository.save(created);

        if (wasNew) {
            log.info("REGION 신규 추가 - sgisAdmCd: {}, dongName: {}, adongCd: {}", sgisAdmCd, dongName, realAdongCd);
            return ApplyResult.of(ApplyOutcome.NEWLY_ADDED);
        }
        return ApplyResult.of(ApplyOutcome.CORRECTED);
    }

    public enum ApplyOutcome {
        ALREADY_CORRECT, CORRECTED, NEWLY_ADDED, CONFLICT
    }

    public record ApplyResult(ApplyOutcome outcome, RegionCrosswalkReport.ConflictEntry conflict) {
        static ApplyResult of(ApplyOutcome outcome) {
            return new ApplyResult(outcome, null);
        }

        static ApplyResult conflict(RegionCrosswalkReport.ConflictEntry conflict) {
            return new ApplyResult(ApplyOutcome.CONFLICT, conflict);
        }
    }
}
