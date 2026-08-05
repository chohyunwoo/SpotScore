package com.spotscore.collector;

import com.spotscore.collector.dto.StoreItemDto;
import com.spotscore.collector.dto.StoreZoneResponse;
import com.spotscore.config.StoreZoneProperties;
import com.spotscore.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 소상공인시장진흥공단 상가(상권)정보 storeListInDong으로 행정구역별 업소 목록을
 * 수집한다. 1회 응답 최대 1,000건 제약이 있어 totalCount를 넘을 때까지 페이징한다.
 */
@Component
public class StoreZoneCollector implements DataCollector<StoreItemDto> {

    private static final Logger log = LoggerFactory.getLogger(StoreZoneCollector.class);
    private static final int PAGE_SIZE = 1000;

    // 실제 호출로 확인: 유효하지 않은/일치하는 데이터가 없는 key를 조회하면 정상 응답
    // 코드로 "03"(NODATA_ERROR)을 내려준다 (진짜 오류가 아니라 결과 없음). 다른 코드는
    // 실제 오류로 취급한다.
    private static final String RESULT_CODE_SUCCESS = "00";
    private static final String RESULT_CODE_NO_DATA = "03";

    // API 명세서 5.3절에서 집계 단위가 행정동(adongCd)으로 확정됨 - divId 후보 중
    // ctprvnCd/signguCd는 사용하지 않는다.
    private static final String DIV_ID = "adongCd";

    // 실제로 서울 전체 규모(수백 개 지역)로 호출했을 때 상권정보 API가 "429 Too Many
    // Requests"를 반환하는 것을 확인했다(discovery 단계에서 페이싱 없이 돌렸을 때).
    // 페이지 단위 호출에도 같은 위험이 있어 429에 한해 지수 백오프로 재시도한다.
    private static final int MAX_RETRY_ATTEMPTS = 4;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(1000);

    private final WebClient storeZoneWebClient;
    private final StoreZoneProperties properties;

    public StoreZoneCollector(@Qualifier("storeZoneWebClient") WebClient storeZoneWebClient, StoreZoneProperties properties) {
        this.storeZoneWebClient = storeZoneWebClient;
        this.properties = properties;
    }

    @Override
    public DataSourceType sourceType() {
        return DataSourceType.STORE_ZONE;
    }

    @Override
    public Flux<StoreItemDto> collect(String regionCode) {
        AtomicLong cumulativeCount = new AtomicLong(0);
        return fetchPage(regionCode, 1, cumulativeCount)
                .expand(page -> {
                    StoreZoneResponse.Body body = page.body();
                    boolean hasMore = body != null && (long) body.pageNo() * PAGE_SIZE < body.totalCount();
                    return hasMore ? fetchPage(regionCode, body.pageNo() + 1, cumulativeCount) : Mono.empty();
                })
                .flatMap(page -> {
                    List<StoreItemDto> items = page.body() == null || page.body().items() == null
                            ? List.of()
                            : page.body().items();
                    return Flux.fromIterable(items);
                });
    }

    private Mono<StoreZoneResponse> fetchPage(String regionCode, int pageNo, AtomicLong cumulativeCount) {
        log.debug("상권정보 API 요청 시작 - adongCd: {}, page: {}", regionCode, pageNo);
        return storeZoneWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/storeListInDong")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("type", "json")
                        .queryParam("divId", DIV_ID)
                        .queryParam("key", regionCode)
                        .queryParam("numOfRows", PAGE_SIZE)
                        .queryParam("pageNo", pageNo)
                        .build())
                .retrieve()
                .bodyToMono(StoreZoneResponse.class)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_BACKOFF)
                        .filter(ex -> ex instanceof WebClientResponseException.TooManyRequests)
                        .doBeforeRetry(signal -> log.warn(
                                "상권정보 429 Too Many Requests - 재시도 {}회차, adongCd: {}, page: {}",
                                signal.totalRetriesInARow() + 1, regionCode, pageNo)))
                .doOnNext(response -> {
                    StoreZoneResponse.Body body = response.body();
                    int count = body == null || body.items() == null ? 0 : body.items().size();
                    long cumulative = cumulativeCount.addAndGet(count);
                    log.info("상권정보 응답 수신 - adongCd: {}, page: {}, 건수: {}", regionCode, pageNo, count);
                    log.debug("상권정보 페이징 처리 중 - adongCd: {}, page: {}, 누적 수집 건수: {}", regionCode, pageNo, cumulative);
                })
                .flatMap(response -> {
                    String resultCode = response.header() == null ? null : response.header().resultCode();
                    if (RESULT_CODE_SUCCESS.equals(resultCode)) {
                        return Mono.just(response);
                    }
                    if (RESULT_CODE_NO_DATA.equals(resultCode)) {
                        log.info("상권정보 응답 0건 - adongCd: {}, page: {}, resultCode: {} (NODATA_ERROR, 정상적인 빈 결과로 처리)",
                                regionCode, pageNo, resultCode);
                        return Mono.just(response);
                    }
                    log.error("상권정보 응답 실패 - adongCd: {}, page: {}, resultCode: {}, resultMsg: {}",
                            regionCode, pageNo, resultCode,
                            response.header() == null ? null : response.header().resultMsg());
                    return Mono.error(new ExternalApiException("STORE_ZONE", "resultCode=" + resultCode));
                })
                .onErrorMap(ex -> !(ex instanceof ExternalApiException), ex -> {
                    log.error("상권정보 API 호출 중 예외 발생 - adongCd: {}, page: {}", regionCode, pageNo, ex);
                    return new ExternalApiException("STORE_ZONE", "상권정보 조회 실패: " + ex.getMessage(), ex);
                });
    }
}
