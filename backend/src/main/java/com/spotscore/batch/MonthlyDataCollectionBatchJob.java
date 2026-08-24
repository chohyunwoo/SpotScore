package com.spotscore.batch;

import com.spotscore.batch.mapping.MappingValidationResult;
import com.spotscore.batch.mapping.RegionCodeMappingValidator;
import com.spotscore.config.BatchProperties;
import com.spotscore.config.TargetRegion;
import com.spotscore.domain.Region;
import com.spotscore.repository.RegionRepository;
import com.spotscore.scoring.ScoreCalculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * SGIS(인구+가구 통계) + 상권정보(업소 목록)를 월 1회 실제 호출해 원자료 테이블에
 * 저장하고, 저장이 끝나면 점수 재계산까지 트리거한다 (CLAUDE.md 5계층 데이터 흐름:
 * 외부 API → 배치 → 저장 → API → 프론트).
 *
 * 실시간 API 호출 금지 원칙에 따라 외부 호출은 이 배치 계층에서만 일어난다.
 */
@Component
public class MonthlyDataCollectionBatchJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyDataCollectionBatchJob.class);

    private final BatchProperties batchProperties;
    private final RegionCodeMappingValidator mappingValidator;
    private final RegionRepository regionRepository;
    private final RegionPersistenceService regionPersistenceService;
    private final ScoreCalculationService scoreCalculationService;

    public MonthlyDataCollectionBatchJob(BatchProperties batchProperties,
                                          RegionCodeMappingValidator mappingValidator,
                                          RegionRepository regionRepository,
                                          RegionPersistenceService regionPersistenceService,
                                          ScoreCalculationService scoreCalculationService) {
        this.batchProperties = batchProperties;
        this.mappingValidator = mappingValidator;
        this.regionRepository = regionRepository;
        this.regionPersistenceService = regionPersistenceService;
        this.scoreCalculationService = scoreCalculationService;
    }

    @Scheduled(cron = "${spotscore.batch.cron}")
    public void runScheduled() {
        run(LocalDate.now());
    }

    private void pace() {
        try {
            Thread.sleep(batchProperties.requestIntervalMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public BatchResult run(LocalDate snapshotDate) {
        Instant startedAt = Instant.now();
        log.info("배치 시작 - snapshotDate: {}, 시작 시각: {}", snapshotDate, startedAt);

        List<String> rawTargets = resolveRawTargets();
        if (rawTargets.isEmpty()) {
            log.warn("배치 대상 지역이 설정되지 않음 - spotscore.batch.target-regions도 비어있고, " +
                    "REGION 테이블에도 sgisAdmCd가 매핑된 지역이 없음");
        }

        int regionsCollected = 0;
        int regionsSkipped = 0;
        int populationRowsSaved = 0;
        int storeCountRowsSaved = 0;
        int ageStatRowsSaved = 0;

        for (String raw : rawTargets) {
            pace();
            try {
                TargetRegion target = TargetRegion.parse(raw);
                MappingValidationResult mapping = mappingValidator.validate(target);
                if (!mapping.valid()) {
                    regionsSkipped++;
                    continue;
                }
                RegionCollectionOutcome outcome = regionPersistenceService.persistRegionData(mapping, snapshotDate);
                regionsCollected++;
                populationRowsSaved += outcome.populationRowsSaved();
                storeCountRowsSaved += outcome.storeCountRowsSaved();
                ageStatRowsSaved += outcome.ageStatRowsSaved();
            } catch (Exception ex) {
                log.error("배치 실패 - target: {} 처리 중 예외 발생", raw, ex);
                regionsSkipped++;
            }
        }

        log.info("수집 결과 요약 - 대상 {}건, 수집 성공 {}건, 스킵 {}건, population_stat 저장 {}건, store_count 저장 {}건, " +
                        "age_stat 저장 {}건",
                rawTargets.size(), regionsCollected, regionsSkipped, populationRowsSaved, storeCountRowsSaved,
                ageStatRowsSaved);

        try {
            scoreCalculationService.recalculateAll(snapshotDate.getYear(), snapshotDate);
        } catch (Exception ex) {
            log.error("배치 실패 - 점수 재계산 단계에서 예외 발생", ex);
        }

        Instant finishedAt = Instant.now();
        long elapsedMillis = Duration.between(startedAt, finishedAt).toMillis();
        log.info("배치 종료 - 종료 시각: {}, 총 소요시간: {}ms", finishedAt, elapsedMillis);

        return new BatchResult(rawTargets.size(), regionsCollected, regionsSkipped,
                populationRowsSaved, storeCountRowsSaved, ageStatRowsSaved, elapsedMillis);
    }

    // spotscore.batch.target-regions(환경변수)는 dev처럼 소수 지역만 도는 로컬
    // 테스트용 override로 남겨두되, 값이 없으면(prod처럼 서울 전체를 대상으로 할
    // 때) REGION 테이블에서 직접 조회해 구성한다 - "sgisAdmCd:adongCd" 쌍을
    // 426개씩 환경변수 문자열로 나열하는 건 그 자체로 CLAUDE.md 확장성 원칙 2
    // ("지역을 하드코딩하지 않는다")를 어기는 것이라, RegionCrosswalkRebuildService가
    // 이미 채워둔 region.sgisAdmCd를 그대로 재사용한다(#9).
    List<String> resolveRawTargets() {
        List<String> configured = batchProperties.targetRegions();
        if (!configured.isEmpty()) {
            log.info("배치 대상 지역 - 설정값(spotscore.batch.target-regions) {}건 사용", configured.size());
            return configured;
        }

        List<Region> regionsWithCrosswalk = regionRepository.findAllBySgisAdmCdIsNotNull();
        if (regionsWithCrosswalk.isEmpty()) {
            return List.of();
        }
        log.info("배치 대상 지역 - spotscore.batch.target-regions 미설정, REGION 테이블의 sgisAdmCd 매핑 " +
                "{}건을 배치 대상으로 사용", regionsWithCrosswalk.size());
        return regionsWithCrosswalk.stream()
                .map(region -> region.getSgisAdmCd() + ":" + region.getRegionCode())
                .toList();
    }
}
