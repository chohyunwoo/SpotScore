package com.spotscore.batch;

import com.spotscore.batch.mapping.MappingValidationResult;
import com.spotscore.batch.mapping.RegionCodeMappingValidator;
import com.spotscore.collector.dto.SgisPopulationDto;
import com.spotscore.collector.dto.StoreItemDto;
import com.spotscore.config.BatchProperties;
import com.spotscore.config.TargetRegion;
import com.spotscore.domain.IndustryCategory;
import com.spotscore.domain.PopulationStat;
import com.spotscore.domain.Region;
import com.spotscore.domain.StoreCount;
import com.spotscore.repository.IndustryCategoryRepository;
import com.spotscore.repository.PopulationStatRepository;
import com.spotscore.repository.RegionRepository;
import com.spotscore.repository.StoreCountRepository;
import com.spotscore.scoring.ScoreCalculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final IndustryCategoryRepository industryCategoryRepository;
    private final PopulationStatRepository populationStatRepository;
    private final StoreCountRepository storeCountRepository;
    private final ScoreCalculationService scoreCalculationService;

    public MonthlyDataCollectionBatchJob(BatchProperties batchProperties,
                                          RegionCodeMappingValidator mappingValidator,
                                          RegionRepository regionRepository,
                                          IndustryCategoryRepository industryCategoryRepository,
                                          PopulationStatRepository populationStatRepository,
                                          StoreCountRepository storeCountRepository,
                                          ScoreCalculationService scoreCalculationService) {
        this.batchProperties = batchProperties;
        this.mappingValidator = mappingValidator;
        this.regionRepository = regionRepository;
        this.industryCategoryRepository = industryCategoryRepository;
        this.populationStatRepository = populationStatRepository;
        this.storeCountRepository = storeCountRepository;
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

        List<String> rawTargets = batchProperties.targetRegions();
        if (rawTargets.isEmpty()) {
            log.warn("배치 대상 지역이 설정되지 않음 - spotscore.batch.target-regions 확인 필요");
        }

        int regionsCollected = 0;
        int regionsSkipped = 0;
        int populationRowsSaved = 0;
        int storeCountRowsSaved = 0;

        for (String raw : rawTargets) {
            pace();
            try {
                TargetRegion target = TargetRegion.parse(raw);
                MappingValidationResult mapping = mappingValidator.validate(target);
                if (!mapping.valid()) {
                    regionsSkipped++;
                    continue;
                }
                RegionCollectionOutcome outcome = persistRegionData(mapping, snapshotDate);
                regionsCollected++;
                populationRowsSaved += outcome.populationRowsSaved();
                storeCountRowsSaved += outcome.storeCountRowsSaved();
            } catch (Exception ex) {
                log.error("배치 실패 - target: {} 처리 중 예외 발생", raw, ex);
                regionsSkipped++;
            }
        }

        log.info("수집 결과 요약 - 대상 {}건, 수집 성공 {}건, 스킵 {}건, population_stat 저장 {}건, store_count 저장 {}건",
                rawTargets.size(), regionsCollected, regionsSkipped, populationRowsSaved, storeCountRowsSaved);

        try {
            scoreCalculationService.recalculateAll(snapshotDate.getYear(), snapshotDate);
        } catch (Exception ex) {
            log.error("배치 실패 - 점수 재계산 단계에서 예외 발생", ex);
        }

        Instant finishedAt = Instant.now();
        long elapsedMillis = Duration.between(startedAt, finishedAt).toMillis();
        log.info("배치 종료 - 종료 시각: {}, 총 소요시간: {}ms", finishedAt, elapsedMillis);

        return new BatchResult(rawTargets.size(), regionsCollected, regionsSkipped,
                populationRowsSaved, storeCountRowsSaved, elapsedMillis);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected RegionCollectionOutcome persistRegionData(MappingValidationResult mapping, LocalDate snapshotDate) {
        TargetRegion target = mapping.target();
        if (mapping.populationRows().size() > 1) {
            log.warn("SGIS 응답에 예상보다 많은 {}건이 반환됨 - sgisAdmCd: {}에 대해 첫 번째 행만 사용",
                    mapping.populationRows().size(), target.sgisAdmCd());
        }
        SgisPopulationDto populationDto = mapping.populationRows().get(0);

        Region region = regionRepository.findById(target.adongCd())
                .map(existing -> {
                    existing.updateName(resolveRegionName(mapping));
                    existing.updateSgisAdmCd(target.sgisAdmCd());
                    return existing;
                })
                .orElseGet(() -> regionRepository.save(
                        new Region(target.adongCd(), resolveRegionName(mapping), "ADONG", target.sgisAdmCd())));

        savePopulationStat(region, populationDto, snapshotDate.getYear());
        int storeCountRowsSaved = saveStoreCounts(region, mapping.storeItems(), snapshotDate);

        return new RegionCollectionOutcome(1, storeCountRowsSaved);
    }

    private String resolveRegionName(MappingValidationResult mapping) {
        if (!mapping.storeItems().isEmpty() && mapping.storeItems().get(0).adongName() != null
                && !mapping.storeItems().get(0).adongName().isBlank()) {
            return mapping.storeItems().get(0).adongName();
        }
        return mapping.populationRows().get(0).admNm();
    }

    private void savePopulationStat(Region region, SgisPopulationDto dto, int year) {
        String regionCode = region.getRegionCode();
        Long totalPopulation = SgisValueParser.parseLong(regionCode, "tot_ppltn", dto.totalPopulation());
        BigDecimal density = SgisValueParser.parseBigDecimal(regionCode, "ppltn_dnsty", dto.populationDensity());
        Long totalFamily = SgisValueParser.parseLong(regionCode, "tot_family", dto.totalFamily());
        Double avgFamilyMemberCount = SgisValueParser.parseDouble(regionCode, "avg_fmember_cnt", dto.avgFamilyMemberCount());

        populationStatRepository.findByRegionAndYear(region, year)
                .ifPresentOrElse(
                        existing -> existing.update(totalPopulation, density, totalFamily, avgFamilyMemberCount),
                        () -> populationStatRepository.save(
                                new PopulationStat(region, year, totalPopulation, density, totalFamily, avgFamilyMemberCount))
                );
    }

    private int saveStoreCounts(Region region, List<StoreItemDto> storeItems, LocalDate snapshotDate) {
        Map<String, List<StoreItemDto>> byIndustry = storeItems.stream()
                .filter(item -> item.industryMediumCode() != null && !item.industryMediumCode().isBlank())
                .collect(Collectors.groupingBy(StoreItemDto::industryMediumCode));

        int saved = 0;
        for (Map.Entry<String, List<StoreItemDto>> entry : byIndustry.entrySet()) {
            String industryCode = entry.getKey();
            List<StoreItemDto> items = entry.getValue();
            String industryName = items.stream()
                    .map(StoreItemDto::industryMediumName)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElseGet(() -> {
                        log.warn("업종명 매핑 실패 - industryCode: {}에 대한 indsMclsNm 없음, 코드를 이름으로 대체", industryCode);
                        return industryCode;
                    });

            IndustryCategory industry = industryCategoryRepository.findById(industryCode)
                    .map(existing -> {
                        existing.updateName(industryName);
                        return existing;
                    })
                    .orElseGet(() -> industryCategoryRepository.save(new IndustryCategory(industryCode, industryName, "MEDIUM")));

            int count = items.size();
            storeCountRepository.findByRegionAndIndustryAndSnapshotDate(region, industry, snapshotDate)
                    .ifPresentOrElse(
                            existing -> existing.updateCount(count),
                            () -> storeCountRepository.save(new StoreCount(region, industry, count, snapshotDate))
                    );
            saved++;
        }
        return saved;
    }
}
