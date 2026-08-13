package com.spotscore.batch;

import com.spotscore.batch.mapping.MappingValidationResult;
import com.spotscore.batch.mapping.RegionCodeMappingValidator;
import com.spotscore.collector.KosisAgeCollector;
import com.spotscore.collector.dto.KosisAgeStatItemDto;
import com.spotscore.collector.dto.SgisPopulationDto;
import com.spotscore.collector.dto.StoreItemDto;
import com.spotscore.config.BatchProperties;
import com.spotscore.config.TargetRegion;
import com.spotscore.domain.AgeStat;
import com.spotscore.domain.IndustryCategory;
import com.spotscore.domain.PopulationStat;
import com.spotscore.domain.Region;
import com.spotscore.domain.Store;
import com.spotscore.domain.StoreCount;
import com.spotscore.exception.ExternalApiException;
import com.spotscore.repository.AgeStatRepository;
import com.spotscore.repository.IndustryCategoryRepository;
import com.spotscore.repository.PopulationStatRepository;
import com.spotscore.repository.RegionRepository;
import com.spotscore.repository.StoreCountRepository;
import com.spotscore.repository.StoreRepository;
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
    private final StoreRepository storeRepository;
    private final AgeStatRepository ageStatRepository;
    private final KosisAgeCollector kosisAgeCollector;
    private final ScoreCalculationService scoreCalculationService;

    public MonthlyDataCollectionBatchJob(BatchProperties batchProperties,
                                          RegionCodeMappingValidator mappingValidator,
                                          RegionRepository regionRepository,
                                          IndustryCategoryRepository industryCategoryRepository,
                                          PopulationStatRepository populationStatRepository,
                                          StoreCountRepository storeCountRepository,
                                          StoreRepository storeRepository,
                                          AgeStatRepository ageStatRepository,
                                          KosisAgeCollector kosisAgeCollector,
                                          ScoreCalculationService scoreCalculationService) {
        this.batchProperties = batchProperties;
        this.mappingValidator = mappingValidator;
        this.regionRepository = regionRepository;
        this.industryCategoryRepository = industryCategoryRepository;
        this.populationStatRepository = populationStatRepository;
        this.storeCountRepository = storeCountRepository;
        this.storeRepository = storeRepository;
        this.ageStatRepository = ageStatRepository;
        this.kosisAgeCollector = kosisAgeCollector;
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
                RegionCollectionOutcome outcome = persistRegionData(mapping, snapshotDate);
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
        saveStoreItems(region, mapping.storeItems(), snapshotDate);
        int ageStatRowsSaved = saveAgeStat(region, snapshotDate.getYear(), snapshotDate);

        return new RegionCollectionOutcome(1, storeCountRowsSaved, ageStatRowsSaved);
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

    // KOSIS 연령별 인구 수집·저장은 아직 densityScore/totalScore 등 기존 점수 계산에
    // 반영되지 않는 별도 지표(ageScore, CLAUDE.md 연령 구성 지표 섹션 3단계)라, 실패해도
    // 이 지역의 SGIS/상권정보 수집·점수 재계산 전체를 막지 않고 AGE_STAT 저장만 스킵한다.
    private int saveAgeStat(Region region, int year, LocalDate snapshotDate) {
        List<KosisAgeStatItemDto> items;
        try {
            items = kosisAgeCollector.collect(region.getRegionCode()).collectList().block();
        } catch (ExternalApiException ex) {
            log.error("배치 실패 - regionCode: {} KOSIS 연령별 인구 수집 실패 (age_stat 저장 스킵, 나머지 배치는 계속 진행)",
                    region.getRegionCode(), ex);
            return 0;
        }
        if (items == null || items.isEmpty()) {
            log.warn("age_stat 저장 스킵 - regionCode: {} KOSIS 응답 0건", region.getRegionCode());
            return 0;
        }

        Long kosisTotalPopulation = null;
        long age2039Sum = 0;
        boolean anyAgeBandParsed = false;
        for (KosisAgeStatItemDto item : items) {
            Long value = KosisValueParser.parseLong(region.getRegionCode(), "DT(ageCode=" + item.ageCode() + ")",
                    item.value());
            if ("0".equals(item.ageCode())) {
                kosisTotalPopulation = value;
            } else if (value != null) {
                age2039Sum += value;
                anyAgeBandParsed = true;
            }
        }

        if (kosisTotalPopulation == null) {
            log.warn("age_stat 저장 스킵 - regionCode: {} KOSIS 총인구(C2=0) 값 없음", region.getRegionCode());
            return 0;
        }
        Long age2039Cnt = anyAgeBandParsed ? age2039Sum : null;
        Long finalKosisTotalPopulation = kosisTotalPopulation;

        ageStatRepository.findByRegionAndYear(region, year)
                .ifPresentOrElse(
                        existing -> existing.update(age2039Cnt, finalKosisTotalPopulation, snapshotDate),
                        () -> ageStatRepository.save(new AgeStat(region, year, age2039Cnt, finalKosisTotalPopulation, snapshotDate))
                );
        log.info("age_stat 저장 완료 - regionCode: {}, year: {}, age2039Cnt: {}, kosisTotalPopulation: {}",
                region.getRegionCode(), year, age2039Cnt, finalKosisTotalPopulation);
        return 1;
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

    // STORE_COUNT(집계)와 별개로 지도 개별 마커용 원본 업소 행을 저장한다. 이미
    // saveStoreCounts가 받은 것과 같은 응답(mapping.storeItems())을 재활용할 뿐,
    // 상권정보 API를 추가로 호출하지 않는다. STORE_COUNT/점수 계산 로직은 이
    // 메서드와 무관하게 그대로 둔다.
    private void saveStoreItems(Region region, List<StoreItemDto> storeItems, LocalDate snapshotDate) {
        int saved = 0;
        int skippedMissingId = 0;
        int missingCoordinates = 0;

        for (StoreItemDto item : storeItems) {
            if (item.storeId() == null || item.storeId().isBlank()) {
                log.warn("업소 원본 저장 스킵 - regionCode: {}, bizesNm: {} - bizesId 없음",
                        region.getRegionCode(), item.storeName());
                skippedMissingId++;
                continue;
            }
            if (item.industryMediumCode() == null || item.industryMediumCode().isBlank()) {
                log.warn("업소 원본 저장 스킵 - bizesId: {} - indsMclsCd 없음", item.storeId());
                skippedMissingId++;
                continue;
            }

            String industryName = item.industryMediumName() != null && !item.industryMediumName().isBlank()
                    ? item.industryMediumName()
                    : item.industryMediumCode();
            IndustryCategory industry = industryCategoryRepository.findById(item.industryMediumCode())
                    .orElseGet(() -> industryCategoryRepository.save(
                            new IndustryCategory(item.industryMediumCode(), industryName, "MEDIUM")));

            if (item.lon() == null || item.lat() == null) {
                log.warn("업소 원본 좌표 누락 - bizesId: {}, regionCode: {} (지도 마커 표시 불가, 나머지 정보는 저장)",
                        item.storeId(), region.getRegionCode());
                missingCoordinates++;
            }

            storeRepository.findById(item.storeId())
                    .ifPresentOrElse(
                            existing -> existing.update(item.storeName(), industry, industryName, region,
                                    item.lon(), item.lat(), snapshotDate),
                            () -> storeRepository.save(new Store(item.storeId(), item.storeName(), industry,
                                    industryName, region, item.lon(), item.lat(), snapshotDate))
                    );
            saved++;
        }

        log.info("업소 원본 저장 완료 - regionCode: {}, 저장 {}건, 스킵(식별자 누락) {}건, 좌표 누락 {}건",
                region.getRegionCode(), saved, skippedMissingId, missingCoordinates);
    }
}
