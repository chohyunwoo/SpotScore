package com.spotscore.discovery;

import com.spotscore.collector.SgisAdministrativeHierarchyCollector;
import com.spotscore.collector.dto.SgisPopulationDto;
import com.spotscore.collector.dto.StoreItemDto;
import com.spotscore.collector.dto.StoreZoneResponse;
import com.spotscore.config.DiscoveryProperties;
import com.spotscore.config.StoreZoneProperties;
import com.spotscore.domain.Region;
import com.spotscore.repository.RegionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CLAUDE.md "아직 결정되지 않은 사항" 확장 작업: MVP 범위(서울) 전체 행정동에
 * 대해 SGIS adm_cd ↔ 상권정보 adongCd 매핑이 실제로 몇 % 성립하는지 확인하고,
 * 성립하는 지역은 REGION 테이블에 반영한다(좌표 시딩은 기존
 * RegionCoordinateSeedingService가 그대로 이어받는다 - 이 서비스는 그걸 다시
 * 구현하지 않는다).
 *
 * 왜 RegionCodeMappingValidator를 그대로 재사용하지 않았는가: 그 컴포넌트는
 * StoreZoneCollector.collect()를 호출하는데, 이는 해당 동의 상권정보를 totalCount까지
 * "완전히" 페이징한다 - 실제 배치 데이터 수집(월 1회, 대상 몇 곳)에는 맞지만, 서울
 * 전체 ~400여 개 동을 매핑 "존재 여부"만 확인하려고 똑같이 완전 페이징하면 강남구
 * 하나(역삼1동만 14,077건)만으로도 비용이 크다는 게 3주차 실측으로 확인됐다. 그래서
 * 이 서비스는 페이지 1건(numOfRows 소량)만 보는 가벼운 존재/이름 대조로 검증한다 -
 * 완전한 업소 수 데이터가 필요한 지역은 이후 월간 배치가 별도로 채운다.
 *
 * 실제로 페이싱 없이 서울 전체(426개 동)를 돌렸더니 상권정보 API가 곧바로
 * "429 Too Many Requests"를 반환했다(8.7초 만에 종료 - 응답 없이 즉시 실패한
 * 것). 그 결과가 "매핑 실패 95%"로 잘못 집계됐었다 - 실제 매핑 문제가 아니라
 * 요청 페이싱 문제였다. 그래서 호출 사이 간격(requestIntervalMillis)과 429 전용
 * 재시도(backoff)를 추가했다.
 */
@Service
public class SeoulRegionDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(SeoulRegionDiscoveryService.class);
    private static final int LIGHTWEIGHT_SAMPLE_ROWS = 5;

    private final SgisAdministrativeHierarchyCollector hierarchyCollector;
    private final WebClient storeZoneWebClient;
    private final StoreZoneProperties storeZoneProperties;
    private final DiscoveryProperties discoveryProperties;
    private final RegionRepository regionRepository;

    public SeoulRegionDiscoveryService(SgisAdministrativeHierarchyCollector hierarchyCollector,
                                        @Qualifier("storeZoneWebClient") WebClient storeZoneWebClient,
                                        StoreZoneProperties storeZoneProperties,
                                        DiscoveryProperties discoveryProperties,
                                        RegionRepository regionRepository) {
        this.hierarchyCollector = hierarchyCollector;
        this.storeZoneWebClient = storeZoneWebClient;
        this.storeZoneProperties = storeZoneProperties;
        this.discoveryProperties = discoveryProperties;
        this.regionRepository = regionRepository;
    }

    public SeoulDiscoveryReport discoverAndValidateSeoulRegions() {
        Instant startedAt = Instant.now();
        String rootAdmCd = discoveryProperties.rootAdmCd();
        log.info("서울 전체 행정동 발견 시작 - rootAdmCd: {}", rootAdmCd);

        List<SgisPopulationDto> guList = hierarchyCollector.listChildren(rootAdmCd).collectList().block();
        if (guList == null) {
            guList = List.of();
        }
        log.info("서울 구 목록 조회 완료 - {}건", guList.size());

        List<GuWithDongs> guWithDongsList = new ArrayList<>();
        int dongCount = 0;
        for (SgisPopulationDto gu : guList) {
            List<SgisPopulationDto> dongs = hierarchyCollector.listChildren(gu.admCd()).collectList().block();
            if (dongs == null) {
                dongs = List.of();
            }
            guWithDongsList.add(new GuWithDongs(gu, dongs));
            dongCount += dongs.size();
        }
        log.info("서울 전체 행정동(SGIS) 목록 조회 완료 - 구 {}개, 동 {}건", guList.size(), dongCount);

        Map<String, String> guNameToOfficialSignguCd = sampleOfficialSignguCodes(rootAdmCd, guList.size());
        log.info("상권정보 공식 시군구코드 샘플링 완료 - {}건 (SGIS 구 {}개 중)",
                guNameToOfficialSignguCd.size(), guList.size());

        int unresolvedGu = 0;
        int candidateCount = 0;
        int mappingSuccess = 0;
        int mappingFailed = 0;
        int regionsUpserted = 0;

        for (GuWithDongs guWithDongs : guWithDongsList) {
            String officialSignguCd = guNameToOfficialSignguCd.get(guWithDongs.gu().admNm());
            if (officialSignguCd == null) {
                log.warn("공식 시군구코드 매칭 실패 - guName: {} (상권정보 샘플링에서 발견되지 않음), 이 구의 동 {}건을 매핑 후보에서 제외",
                        guWithDongs.gu().admNm(), guWithDongs.dongs().size());
                unresolvedGu++;
                continue;
            }

            for (SgisPopulationDto dong : guWithDongs.dongs()) {
                if (dong.admCd() == null || dong.admCd().length() <= 5) {
                    log.warn("행정동 코드 형식 이상 - sgisAdmCd: {}, admNm: {} (5자리 초과 예상, 후보에서 제외)",
                            dong.admCd(), dong.admNm());
                    continue;
                }
                String dongSuffix = dong.admCd().substring(5);
                String candidateAdongCd = officialSignguCd + dongSuffix;
                candidateCount++;

                MappingCheckOutcome outcome = checkMappingLightweight(dong, candidateAdongCd);
                if (outcome.valid()) {
                    mappingSuccess++;
                    if (upsertRegion(candidateAdongCd, dong.admCd(), outcome.resolvedRegionName())) {
                        regionsUpserted++;
                    }
                } else {
                    mappingFailed++;
                }
            }
        }

        double failureRate = candidateCount == 0 ? 0.0 : (100.0 * mappingFailed / candidateCount);
        long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
        log.info("서울 전체 매핑 검증 종료 - 후보 {}건(매칭 안 된 구 {}개 제외), 성공 {}건, 실패 {}건, 실패율 {}%, region 반영 {}건, 소요시간: {}ms",
                candidateCount, unresolvedGu, mappingSuccess, mappingFailed,
                String.format("%.1f", failureRate), regionsUpserted, elapsedMillis);

        return new SeoulDiscoveryReport(guList.size(), dongCount, unresolvedGu, candidateCount,
                mappingSuccess, mappingFailed, failureRate, regionsUpserted, elapsedMillis);
    }

    /**
     * 상권정보에는 "시군구 목록 전체 조회" 오퍼레이션이 없어, divId=ctprvnCd로
     * 서울 전체를 조회하며 등장하는 signguCd/signguNm을 표본으로 수집한다.
     * SGIS가 실제로 찾아낸 구 개수(expectedGuCount)에 도달하면 더 조회하지 않는다 -
     * "서울은 25개 구"라는 지식을 코드에 박아넣지 않고, 방금 실제로 조회한 값을 쓴다.
     */
    // package-private(private 아님)로 둔 이유: RegionCrosswalkRebuildService(같은
    // discovery 패키지)가 SGIS 구 이름 -> 상권정보 실제 signguCd 대응이 필요할 때
    // 이 메서드를 그대로 재사용한다 - 로직을 복제하지 않기 위함이고, 동작 자체는
    // 바뀌지 않았다.
    Map<String, String> sampleOfficialSignguCodes(String rootAdmCd, int expectedGuCount) {
        Map<String, String> nameToCode = new LinkedHashMap<>();
        int maxPages = discoveryProperties.signguSamplePages();
        for (int page = 1; page <= maxPages; page++) {
            StoreZoneResponse response = fetchStoreZonePage("ctprvnCd", rootAdmCd, 1000, page);
            if (response == null) {
                break;
            }
            String resultCode = response.header() == null ? null : response.header().resultCode();
            if (!"00".equals(resultCode)) {
                if (!"03".equals(resultCode)) {
                    log.warn("상권정보 시군구코드 샘플링 중단 - page: {}, resultCode: {}", page, resultCode);
                }
                break;
            }
            List<StoreItemDto> items = response.body() == null || response.body().items() == null
                    ? List.of() : response.body().items();
            if (items.isEmpty()) {
                break;
            }
            for (StoreItemDto item : items) {
                if (item.signguCode() != null && item.signguName() != null) {
                    nameToCode.putIfAbsent(item.signguName(), item.signguCode());
                }
            }
            log.debug("상권정보 시군구코드 샘플링 진행 중 - page: {}, 누적 고유 시군구 {}건", page, nameToCode.size());
            if (expectedGuCount > 0 && nameToCode.size() >= expectedGuCount) {
                log.info("상권정보 시군구코드 샘플링 조기 종료 - {}페이지만에 {}개 시군구 모두 발견", page, expectedGuCount);
                break;
            }
        }
        return nameToCode;
    }

    /**
     * 완전 페이징(StoreZoneCollector) 대신 page 1, numOfRows 소량으로만 조회해
     * "이 코드가 실제로 존재하고 SGIS와 같은 지역을 가리키는지"만 가볍게 확인한다.
     */
    private MappingCheckOutcome checkMappingLightweight(SgisPopulationDto sgisDong, String candidateAdongCd) {
        StoreZoneResponse response = fetchStoreZonePage("adongCd", candidateAdongCd, LIGHTWEIGHT_SAMPLE_ROWS, 1);
        if (response == null) {
            log.warn("행정구역 코드 매핑 실패(라이트) - sgisAdmCd: {}, candidateAdongCd: {}, 사유: 상권정보 API 호출 실패",
                    sgisDong.admCd(), candidateAdongCd);
            return new MappingCheckOutcome(false, null);
        }

        String resultCode = response.header() == null ? null : response.header().resultCode();
        if (!"00".equals(resultCode) && !"03".equals(resultCode)) {
            log.warn("행정구역 코드 매핑 실패(라이트) - sgisAdmCd: {}, candidateAdongCd: {}, resultCode: {}",
                    sgisDong.admCd(), candidateAdongCd, resultCode);
            return new MappingCheckOutcome(false, null);
        }

        List<StoreItemDto> items = response.body() == null || response.body().items() == null
                ? List.of() : response.body().items();
        if (items.isEmpty()) {
            // 버그 수정(REGION 재구축 진단에서 발견): 0건(NODATA_ERROR)을 예전엔 "정합성
            // 확인 불가 = 매핑 성립"으로 간주해 SGIS 이름만으로 region에 반영했다. 이 판단이
            // 틀린 후보 코드까지 조용히 통과시킨 원인이었다(진단 결과 실패 지역 351건 중 212건이
            // 바로 이 경로로 잘못 저장됨). 실제 데이터로 확인되지 않는 한 매핑 성립으로 보지
            // 않도록 실패 처리한다 - 진짜 0건인 지역은 RegionCrosswalkRebuildService의 구
            // 단위 전체 스캔(divId=signguCd)으로만 확정한다.
            log.warn("행정구역 코드 매핑 실패(라이트) - sgisAdmCd: {}, candidateAdongCd: {}, 사유: 상권정보 응답 0건(NODATA) - " +
                            "이 가벼운 조회만으로는 진짜 0건인지 코드 자체가 틀렸는지 구분 불가하므로 매핑 성립으로 간주하지 않음",
                    sgisDong.admCd(), candidateAdongCd);
            return new MappingCheckOutcome(false, null);
        }

        StoreItemDto sample = items.get(0);
        if (sample.adongCode() != null && !sample.adongCode().equals(candidateAdongCd)) {
            log.warn("행정구역 코드 매핑 불일치(라이트) - candidateAdongCd: {}, 응답 adongCd: {}",
                    candidateAdongCd, sample.adongCode());
            return new MappingCheckOutcome(false, null);
        }

        String admNm = sgisDong.admNm();
        if (admNm != null && sample.adongName() != null && !admNm.contains(sample.adongName())) {
            log.warn("행정구역 코드 매핑 불일치(라이트) - sgisAdmNm: {} vs adongNm: {} (candidateAdongCd: {})",
                    admNm, sample.adongName(), candidateAdongCd);
            return new MappingCheckOutcome(false, null);
        }

        String resolvedName = sample.adongName() != null && !sample.adongName().isBlank() ? sample.adongName() : admNm;
        return new MappingCheckOutcome(true, resolvedName);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected boolean upsertRegion(String regionCode, String sgisAdmCd, String regionName) {
        String name = regionName == null || regionName.isBlank() ? regionCode : regionName;
        boolean isNew = regionRepository.findById(regionCode)
                .map(existing -> {
                    existing.updateName(name);
                    existing.updateSgisAdmCd(sgisAdmCd);
                    return false;
                })
                .orElseGet(() -> {
                    regionRepository.save(new Region(regionCode, name, "ADONG", sgisAdmCd));
                    return true;
                });
        log.debug("region 반영 - regionCode: {}, sgisAdmCd: {}, name: {}, 신규 여부: {}", regionCode, sgisAdmCd, name, isNew);
        return true;
    }

    private StoreZoneResponse fetchStoreZonePage(String divId, String key, int numOfRows, int pageNo) {
        pace();
        log.debug("상권정보 조회(발견용) - divId: {}, key: {}, page: {}", divId, key, pageNo);
        return storeZoneWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/storeListInDong")
                        .queryParam("serviceKey", storeZoneProperties.serviceKey())
                        .queryParam("type", "json")
                        .queryParam("divId", divId)
                        .queryParam("key", key)
                        .queryParam("numOfRows", numOfRows)
                        .queryParam("pageNo", pageNo)
                        .build())
                .retrieve()
                .bodyToMono(StoreZoneResponse.class)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(1000))
                        .filter(ex -> ex instanceof WebClientResponseException.TooManyRequests)
                        .doBeforeRetry(signal -> log.warn(
                                "429 Too Many Requests - 재시도 {}회차, divId: {}, key: {}",
                                signal.totalRetriesInARow() + 1, divId, key)))
                .onErrorResume(ex -> {
                    log.warn("상권정보 조회 실패(발견용) - divId: {}, key: {}, page: {}, 사유: {}",
                            divId, key, pageNo, ex.getMessage());
                    return Mono.empty();
                })
                .block();
    }

    private void pace() {
        try {
            Thread.sleep(discoveryProperties.requestIntervalMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record GuWithDongs(SgisPopulationDto gu, List<SgisPopulationDto> dongs) {
    }

    private record MappingCheckOutcome(boolean valid, String resolvedRegionName) {
    }
}
