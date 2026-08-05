package com.spotscore.collector;

import com.spotscore.collector.dto.SgisPopulationDto;
import com.spotscore.collector.dto.SgisPopulationResponse;
import com.spotscore.collector.sgis.SgisAuthService;
import com.spotscore.config.SgisProperties;
import com.spotscore.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Year;
import java.util.List;

/**
 * stats/population.json에 low_search=1을 붙여 특정 adm_cd의 하위 행정구역 목록
 * (adm_cd/adm_nm)을 조회한다 - SgisAdministrativeHierarchyCollector는 실제 인구
 * 통계가 아니라 코드/이름 계층 구조를 얻는 데 쓴다(SeoulRegionDiscoveryService).
 *
 * SgisCollector와 같은 엔드포인트를 쓰지만 목적이 다르고(원자료 수집 vs 코드
 * 발견), CLAUDE.md 확장성 원칙 1(기존 구현체 수정 금지, 새 구현체만 추가)에 따라
 * SgisCollector를 건드리지 않고 별도 컴포넌트로 뒀다.
 */
@Component
public class SgisAdministrativeHierarchyCollector {

    private static final Logger log = LoggerFactory.getLogger(SgisAdministrativeHierarchyCollector.class);
    private static final int DEFAULT_STAT_YEAR_OFFSET = 2;

    private final WebClient sgisWebClient;
    private final SgisAuthService sgisAuthService;
    private final SgisProperties properties;

    public SgisAdministrativeHierarchyCollector(@Qualifier("sgisWebClient") WebClient sgisWebClient,
                                                 SgisAuthService sgisAuthService, SgisProperties properties) {
        this.sgisWebClient = sgisWebClient;
        this.sgisAuthService = sgisAuthService;
        this.properties = properties;
    }

    public Flux<SgisPopulationDto> listChildren(String parentAdmCd) {
        return sgisAuthService.getValidAccessToken()
                .flatMapMany(accessToken -> fetchChildren(parentAdmCd, accessToken));
    }

    private Flux<SgisPopulationDto> fetchChildren(String parentAdmCd, String accessToken) {
        int year = resolveStatYear();
        log.debug("SGIS 하위 행정구역 조회 시작 - parentAdmCd: {}, year: {}", parentAdmCd, year);
        return sgisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stats/population.json")
                        .queryParam("accessToken", accessToken)
                        .queryParam("adm_cd", parentAdmCd)
                        .queryParam("year", year)
                        .queryParam("low_search", 1)
                        .build())
                .retrieve()
                .bodyToMono(SgisPopulationResponse.class)
                .doOnNext(response -> {
                    int count = response.result() == null ? 0 : response.result().size();
                    log.info("SGIS 하위 행정구역 응답 수신 - parentAdmCd: {}, 건수: {}", parentAdmCd, count);
                })
                .flatMapMany(response -> {
                    if (response.errCd() != 0) {
                        log.error("SGIS 하위 행정구역 응답 실패 - parentAdmCd: {}, errCd: {}, errMsg: {}",
                                parentAdmCd, response.errCd(), response.errMsg());
                        return Flux.error(new ExternalApiException("SGIS",
                                "errCd=" + response.errCd() + ", errMsg=" + response.errMsg()));
                    }
                    List<SgisPopulationDto> result = response.result() == null ? List.of() : response.result();
                    return Flux.fromIterable(result);
                })
                .onErrorMap(ex -> !(ex instanceof ExternalApiException), ex -> {
                    log.error("SGIS 하위 행정구역 조회 중 예외 발생 - parentAdmCd: {}", parentAdmCd, ex);
                    return new ExternalApiException("SGIS", "하위 행정구역 조회 실패: " + ex.getMessage(), ex);
                });
    }

    private int resolveStatYear() {
        if (properties.statYear() != null) {
            return properties.statYear();
        }
        int fallback = Year.now().getValue() - DEFAULT_STAT_YEAR_OFFSET;
        log.warn("spotscore.sgis.stat-year 미설정 - 기본값 {}년 사용", fallback);
        return fallback;
    }
}
