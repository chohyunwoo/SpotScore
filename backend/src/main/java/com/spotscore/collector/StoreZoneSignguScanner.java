package com.spotscore.collector;

import com.spotscore.collector.dto.AdongCrosswalkEntry;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * storeListInDong을 divId=signguCd(구 단위)로 전체 페이징 조회해, 그 구에 상권정보가
 * 실제로 알고 있는 모든 (adongCd, adongNm) 조합을 뽑는다.
 *
 * StoreZoneCollector(divId=adongCd, 동 단위 전체 페이징)와 목적이 달라 그 클래스를
 * 고치지 않고 새 구현체로 뒀다(CLAUDE.md 확장성 원칙 1). 진단 결과 상권정보의
 * adongCd가 SGIS adm_cd와 완전히 독립된 코드 체계임이 확인돼(REGION 재구축
 * 진단 보고 참고), REGION.region_code를 실제 값으로 교정하려면 "동 코드를
 * 안다고 가정하고 조회"하는 대신 "구 단위로 전체를 훑어 실제 코드 목록을
 * 얻는" 이 방식이 필요하다.
 */
@Component
public class StoreZoneSignguScanner {

    private static final Logger log = LoggerFactory.getLogger(StoreZoneSignguScanner.class);
    private static final int PAGE_SIZE = 1000;
    private static final String RESULT_CODE_SUCCESS = "00";
    private static final String RESULT_CODE_NO_DATA = "03";
    private static final String DIV_ID = "signguCd";
    private static final int MAX_RETRY_ATTEMPTS = 4;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(1000);

    private final WebClient storeZoneWebClient;
    private final StoreZoneProperties properties;

    public StoreZoneSignguScanner(@Qualifier("storeZoneWebClient") WebClient storeZoneWebClient,
                                   StoreZoneProperties properties) {
        this.storeZoneWebClient = storeZoneWebClient;
        this.properties = properties;
    }

    public List<AdongCrosswalkEntry> scan(String signguCd) {
        AtomicLong cumulativeCount = new AtomicLong(0);
        Map<String, String> distinctByCode = new LinkedHashMap<>();
        fetchPage(signguCd, 1, cumulativeCount)
                .expand(page -> {
                    StoreZoneResponse.Body body = page.body();
                    boolean hasMore = body != null && (long) body.pageNo() * PAGE_SIZE < body.totalCount();
                    return hasMore ? fetchPage(signguCd, body.pageNo() + 1, cumulativeCount) : Mono.empty();
                })
                .doOnNext(page -> {
                    List<StoreItemDto> items = page.body() == null || page.body().items() == null
                            ? List.of() : page.body().items();
                    for (StoreItemDto item : items) {
                        if (item.adongCode() != null && item.adongName() != null) {
                            distinctByCode.putIfAbsent(item.adongCode(), item.adongName());
                        }
                    }
                })
                .blockLast();

        log.info("signguCd 전체 스캔 완료 - signguCd: {}, 고유 행정동 {}건", signguCd, distinctByCode.size());
        return distinctByCode.entrySet().stream()
                .map(e -> new AdongCrosswalkEntry(e.getKey(), e.getValue()))
                .toList();
    }

    private Mono<StoreZoneResponse> fetchPage(String signguCd, int pageNo, AtomicLong cumulativeCount) {
        log.debug("signguCd 스캔 요청 시작 - signguCd: {}, page: {}", signguCd, pageNo);
        return storeZoneWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/storeListInDong")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("type", "json")
                        .queryParam("divId", DIV_ID)
                        .queryParam("key", signguCd)
                        .queryParam("numOfRows", PAGE_SIZE)
                        .queryParam("pageNo", pageNo)
                        .build())
                .retrieve()
                .bodyToMono(StoreZoneResponse.class)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_BACKOFF)
                        .filter(ex -> ex instanceof WebClientResponseException.TooManyRequests)
                        .doBeforeRetry(signal -> log.warn(
                                "signguCd 스캔 429 Too Many Requests - 재시도 {}회차, signguCd: {}, page: {}",
                                signal.totalRetriesInARow() + 1, signguCd, pageNo)))
                .doOnNext(response -> {
                    StoreZoneResponse.Body body = response.body();
                    int count = body == null || body.items() == null ? 0 : body.items().size();
                    long cumulative = cumulativeCount.addAndGet(count);
                    log.info("signguCd 스캔 응답 수신 - signguCd: {}, page: {}, 건수: {}, 누적: {}",
                            signguCd, pageNo, count, cumulative);
                })
                .flatMap(response -> {
                    String resultCode = response.header() == null ? null : response.header().resultCode();
                    if (RESULT_CODE_SUCCESS.equals(resultCode) || RESULT_CODE_NO_DATA.equals(resultCode)) {
                        return Mono.just(response);
                    }
                    log.error("signguCd 스캔 실패 - signguCd: {}, page: {}, resultCode: {}, resultMsg: {}",
                            signguCd, pageNo, resultCode,
                            response.header() == null ? null : response.header().resultMsg());
                    return Mono.error(new ExternalApiException("STORE_ZONE", "resultCode=" + resultCode));
                })
                .onErrorMap(ex -> !(ex instanceof ExternalApiException), ex -> {
                    log.error("signguCd 스캔 중 예외 발생 - signguCd: {}, page: {}", signguCd, pageNo, ex);
                    return new ExternalApiException("STORE_ZONE", "signguCd 스캔 실패: " + ex.getMessage(), ex);
                });
    }
}
