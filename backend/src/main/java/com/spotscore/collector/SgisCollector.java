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
 * SGIS stats/population.json으로 행정구역별 인구/가구 통계를 수집한다.
 */
@Component
public class SgisCollector implements DataCollector<SgisPopulationDto> {

    private static final Logger log = LoggerFactory.getLogger(SgisCollector.class);

    // 실제 호출로 확인된 사실: SGIS 통계는 발행 시차가 있어 최신 연도의 다음 해 데이터는
    // 아직 존재하지 않는다. spotscore.sgis.stat-year를 비워두면 이 만큼 과거 연도를
    // 기본값으로 사용한다 (예: statYear 미설정 시 현재 연도-2).
    private static final int DEFAULT_STAT_YEAR_OFFSET = 2;

    private final WebClient sgisWebClient;
    private final SgisAuthService sgisAuthService;
    private final SgisProperties properties;

    public SgisCollector(@Qualifier("sgisWebClient") WebClient sgisWebClient, SgisAuthService sgisAuthService,
                          SgisProperties properties) {
        this.sgisWebClient = sgisWebClient;
        this.sgisAuthService = sgisAuthService;
        this.properties = properties;
    }

    @Override
    public DataSourceType sourceType() {
        return DataSourceType.SGIS;
    }

    @Override
    public Flux<SgisPopulationDto> collect(String regionCode) {
        return sgisAuthService.getValidAccessToken()
                .flatMapMany(accessToken -> fetchPopulation(regionCode, accessToken));
    }

    private Flux<SgisPopulationDto> fetchPopulation(String regionCode, String accessToken) {
        int statYear = resolveStatYear();
        log.debug("SGIS 인구 통계 API 요청 시작 - adm_cd: {}, year: {}", regionCode, statYear);
        return sgisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stats/population.json")
                        .queryParam("accessToken", accessToken)
                        .queryParam("adm_cd", regionCode)
                        .queryParam("year", statYear)
                        .build())
                .retrieve()
                .bodyToMono(SgisPopulationResponse.class)
                .doOnNext(response -> {
                    int count = response.result() == null ? 0 : response.result().size();
                    log.info("SGIS 인구 통계 응답 수신 - adm_cd: {}, 건수: {}", regionCode, count);
                })
                .flatMapMany(response -> {
                    if (response.errCd() != 0) {
                        log.error("SGIS 인구 통계 응답 실패 - adm_cd: {}, errCd: {}, errMsg: {}",
                                regionCode, response.errCd(), response.errMsg());
                        return Flux.error(new ExternalApiException("SGIS",
                                "errCd=" + response.errCd() + ", errMsg=" + response.errMsg()));
                    }
                    List<SgisPopulationDto> result = response.result() == null ? List.of() : response.result();
                    return Flux.fromIterable(result);
                })
                .onErrorMap(ex -> !(ex instanceof ExternalApiException), ex -> {
                    log.error("SGIS 인구 통계 API 호출 중 예외 발생 - adm_cd: {}", regionCode, ex);
                    return new ExternalApiException("SGIS", "인구 통계 조회 실패: " + ex.getMessage(), ex);
                });
    }

    private int resolveStatYear() {
        if (properties.statYear() != null) {
            return properties.statYear();
        }
        int fallback = Year.now().getValue() - DEFAULT_STAT_YEAR_OFFSET;
        log.warn("spotscore.sgis.stat-year 미설정 - 기본값 {}년 사용 (실제 발행 연도 확인 후 명시적으로 설정 권장)", fallback);
        return fallback;
    }
}
