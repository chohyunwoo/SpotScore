package com.spotscore.discovery;

import com.spotscore.collector.SgisAdministrativeHierarchyCollector;
import com.spotscore.collector.StoreZoneSignguScanner;
import com.spotscore.collector.dto.AdongCrosswalkEntry;
import com.spotscore.collector.dto.SgisPopulationDto;
import com.spotscore.config.DiscoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REGION.region_code를 상권정보 API가 실제로 쓰는 adongCd로 재구축한다.
 *
 * 진단 결과(REGION 재구축 진단 보고): 상권정보의 adongCd는 SGIS의 adm_cd와
 * 완전히 독립된 자체 코드 체계라 "구코드만 다르고 동 접미사는 같다"는 변환
 * 공식이 성립하지 않는다(강남구 역삼1·2동처럼 우연히 접미사가 같은 ~140개
 * 지역만 정상 동작). 그래서 "코드를 안다고 가정하고 동 단위로 조회"하는 대신,
 * 구 단위(divId=signguCd)로 전체 페이징해 상권정보가 실제로 아는 (adongCd,
 * adongNm) 전체를 얻고, SGIS 동 이름과 대조해 진짜 코드를 확정한다.
 *
 * SeoulRegionDiscoveryService와 목적이 겹치지만(둘 다 REGION을 채움), 그 서비스는
 * "가벼운 존재 확인"(NODATA=매핑 성립으로 오판하던 버그가 있었음, 3단계에서 수정)
 * 방식이라 이 재구축의 신뢰 기준(전체 스캔 후 이름 대조)에 못 미친다 - 기존
 * 서비스를 고쳐 쓰지 않고 새 서비스로 둔 이유(CLAUDE.md 확장성 원칙 1).
 */
@Service
public class RegionCrosswalkRebuildService {

    private static final Logger log = LoggerFactory.getLogger(RegionCrosswalkRebuildService.class);

    private final SgisAdministrativeHierarchyCollector hierarchyCollector;
    private final StoreZoneSignguScanner signguScanner;
    private final SeoulRegionDiscoveryService seoulRegionDiscoveryService;
    private final DiscoveryProperties discoveryProperties;
    private final RegionMappingCorrector regionMappingCorrector;

    public RegionCrosswalkRebuildService(SgisAdministrativeHierarchyCollector hierarchyCollector,
                                          StoreZoneSignguScanner signguScanner,
                                          SeoulRegionDiscoveryService seoulRegionDiscoveryService,
                                          DiscoveryProperties discoveryProperties,
                                          RegionMappingCorrector regionMappingCorrector) {
        this.hierarchyCollector = hierarchyCollector;
        this.signguScanner = signguScanner;
        this.seoulRegionDiscoveryService = seoulRegionDiscoveryService;
        this.discoveryProperties = discoveryProperties;
        this.regionMappingCorrector = regionMappingCorrector;
    }

    public RegionCrosswalkReport rebuild() {
        Instant startedAt = Instant.now();
        String rootAdmCd = discoveryProperties.rootAdmCd();
        log.info("REGION 크로스워크 재구축 시작 - rootAdmCd: {}", rootAdmCd);

        List<SgisPopulationDto> guList = hierarchyCollector.listChildren(rootAdmCd).collectList().block();
        if (guList == null) {
            guList = List.of();
        }

        Map<String, String> guNameToSignguCd = seoulRegionDiscoveryService.sampleOfficialSignguCodes(rootAdmCd, guList.size());
        log.info("구 코드 대응 완료 - SGIS 구 {}개 중 상권정보 signguCd {}개 확보", guList.size(), guNameToSignguCd.size());

        int sgisDongCount = 0;
        int alreadyCorrect = 0;
        int corrected = 0;
        int newlyAdded = 0;
        int looseMatch = 0;
        List<RegionCrosswalkReport.UnmatchedDong> unmatched = new ArrayList<>();
        List<RegionCrosswalkReport.ConflictEntry> conflicts = new ArrayList<>();

        for (SgisPopulationDto gu : guList) {
            String signguCd = guNameToSignguCd.get(gu.admNm());
            if (signguCd == null) {
                log.warn("구 코드 매칭 실패 - guName: {} (상권정보 샘플링에서 signguCd를 찾지 못함, 이 구 전체를 건너뜀)",
                        gu.admNm());
                continue;
            }

            List<SgisPopulationDto> sgisDongs = hierarchyCollector.listChildren(gu.admCd()).collectList().block();
            if (sgisDongs == null) {
                sgisDongs = List.of();
            }
            sgisDongCount += sgisDongs.size();

            List<AdongCrosswalkEntry> realEntries = signguScanner.scan(signguCd);
            log.info("구 처리 중 - guName: {}, SGIS 동 {}건, 상권정보 실제 동 {}건", gu.admNm(), sgisDongs.size(), realEntries.size());

            for (SgisPopulationDto dong : sgisDongs) {
                MatchResult matchResult = findMatch(dong.admNm(), realEntries);
                if (matchResult == null) {
                    unmatched.add(new RegionCrosswalkReport.UnmatchedDong(gu.admNm(), dong.admCd(), dong.admNm()));
                    log.warn("행정동 이름 매칭 실패 - guName: {}, sgisAdmCd: {}, sgisDongName: {} (상권정보 실제 목록 {}건 중 대응 없음)",
                            gu.admNm(), dong.admCd(), dong.admNm(), realEntries.size());
                    continue;
                }
                if (matchResult.kind() == DongNameMatcher.MatchKind.LOOSE) {
                    looseMatch++;
                    log.info("행정동 이름 완화 대조로 매칭 - sgisDongName: {}, 상권정보 표기: {}, 확정 adongCd: {}",
                            dong.admNm(), matchResult.entry().adongNm(), matchResult.entry().adongCd());
                }

                RegionMappingCorrector.ApplyResult applyResult =
                        regionMappingCorrector.apply(dong.admCd(), dong.admNm(), matchResult.entry().adongCd());
                switch (applyResult.outcome()) {
                    case ALREADY_CORRECT -> alreadyCorrect++;
                    case CORRECTED -> corrected++;
                    case NEWLY_ADDED -> newlyAdded++;
                    case CONFLICT -> conflicts.add(applyResult.conflict());
                }
            }
        }

        long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
        log.info("REGION 크로스워크 재구축 종료 - SGIS 동 {}건, 이미정확 {}건, 교정 {}건, 신규추가 {}건, 완화매칭 {}건, " +
                        "매칭실패 {}건, 코드충돌 {}건, 소요시간: {}ms",
                sgisDongCount, alreadyCorrect, corrected, newlyAdded, looseMatch, unmatched.size(), conflicts.size(), elapsedMillis);

        return new RegionCrosswalkReport(guList.size(), sgisDongCount, alreadyCorrect, corrected, newlyAdded,
                looseMatch, unmatched.size(), unmatched, conflicts.size(), conflicts, elapsedMillis);
    }

    private MatchResult findMatch(String sgisDongName, List<AdongCrosswalkEntry> realEntries) {
        for (AdongCrosswalkEntry entry : realEntries) {
            if (DongNameMatcher.match(sgisDongName, entry.adongNm()) == DongNameMatcher.MatchKind.STRICT) {
                return new MatchResult(entry, DongNameMatcher.MatchKind.STRICT);
            }
        }

        AdongCrosswalkEntry looseCandidate = null;
        int looseCandidateCount = 0;
        for (AdongCrosswalkEntry entry : realEntries) {
            if (DongNameMatcher.match(sgisDongName, entry.adongNm()) == DongNameMatcher.MatchKind.LOOSE) {
                looseCandidate = entry;
                looseCandidateCount++;
            }
        }
        if (looseCandidateCount == 1) {
            return new MatchResult(looseCandidate, DongNameMatcher.MatchKind.LOOSE);
        }
        if (looseCandidateCount > 1) {
            log.warn("행정동 이름 완화 대조 모호함 - sgisDongName: {} 후보 {}건 발견, 자동 확정 보류(매칭 실패로 처리)",
                    sgisDongName, looseCandidateCount);
        }
        return null;
    }

    private record MatchResult(AdongCrosswalkEntry entry, DongNameMatcher.MatchKind kind) {
    }
}
