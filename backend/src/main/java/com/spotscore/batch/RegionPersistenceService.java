package com.spotscore.batch;

import com.spotscore.batch.mapping.MappingValidationResult;
import com.spotscore.collector.KosisAgeCollector;
import com.spotscore.collector.dto.KosisAgeStatItemDto;
import com.spotscore.collector.dto.SgisPopulationDto;
import com.spotscore.collector.dto.StoreItemDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 지역 1건의 원자료(인구/가구·연령·업소 집계·업소 원본)를 DB에 저장하는 책임만 담당한다.
 *
 * <p>왜 {@link MonthlyDataCollectionBatchJob}에서 분리했는가: 저장은 지역 단위로 하나의
 * 트랜잭션({@code REQUIRES_NEW})으로 묶여야 갱신(dirty checking) 반영과 부분 실패 롤백이
 * 보장된다. 그런데 같은 빈 안의 메서드를 self-invocation으로 호출하면 Spring 프록시가
 * 우회되고, {@code protected} 메서드의 {@code @Transactional}은 기본 프록시 모드에서
 * 무시되어 트랜잭션이 아예 걸리지 않는다. 그러면 {@code findById}로 얻은 detached 엔티티의
 * 갱신이 flush되지 않아, 월간 재수집 시 기존 행 갱신이 조용히 유실된다(이슈 #32).
 * 별도 빈의 <b>public</b> 메서드로 두어 실제 프록시를 거친 트랜잭션이 걸리도록 한다.
 */
@Service
public class RegionPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(RegionPersistenceService.class);

    private final RegionRepository regionRepository;
    private final IndustryCategoryRepository industryCategoryRepository;
    private final PopulationStatRepository populationStatRepository;
    private final StoreCountRepository storeCountRepository;
    private final StoreRepository storeRepository;
    private final AgeStatRepository ageStatRepository;
    private final KosisAgeCollector kosisAgeCollector;

    public RegionPersistenceService(RegionRepository regionRepository,
                                    IndustryCategoryRepository industryCategoryRepository,
                                    PopulationStatRepository populationStatRepository,
                                    StoreCountRepository storeCountRepository,
                                    StoreRepository storeRepository,
                                    AgeStatRepository ageStatRepository,
                                    KosisAgeCollector kosisAgeCollector) {
        this.regionRepository = regionRepository;
        this.industryCategoryRepository = industryCategoryRepository;
        this.populationStatRepository = populationStatRepository;
        this.storeCountRepository = storeCountRepository;
        this.storeRepository = storeRepository;
        this.ageStatRepository = ageStatRepository;
        this.kosisAgeCollector = kosisAgeCollector;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RegionCollectionOutcome persistRegionData(MappingValidationResult mapping, LocalDate snapshotDate) {
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
