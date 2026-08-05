package com.spotscore.collector.sgis;

import com.spotscore.collector.dto.SgisAuthResponse;
import com.spotscore.config.SgisProperties;
import com.spotscore.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

/**
 * SGIS accessToken 발급/캐싱/재발급을 담당한다. accessToken은 만료 시각이 있으므로
 * 만료 전에는 캐시된 값을 재사용하고, 만료(임박) 시에만 재발급을 요청한다.
 */
@Service
public class SgisAuthService {

    private static final Logger log = LoggerFactory.getLogger(SgisAuthService.class);

    private final WebClient sgisWebClient;
    private final SgisProperties properties;
    private final AtomicReference<SgisToken> cachedToken = new AtomicReference<>();

    public SgisAuthService(@Qualifier("sgisWebClient") WebClient sgisWebClient, SgisProperties properties) {
        this.sgisWebClient = sgisWebClient;
        this.properties = properties;
    }

    public Mono<String> getValidAccessToken() {
        SgisToken token = cachedToken.get();
        if (token != null && token.isValid()) {
            return Mono.just(token.accessToken());
        }
        return issueToken().map(SgisToken::accessToken);
    }

    private Mono<SgisToken> issueToken() {
        log.debug("SGIS AccessToken 발급 요청 시작");
        return sgisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/auth/authentication.json")
                        .queryParam("consumer_key", properties.consumerKey())
                        .queryParam("consumer_secret", properties.consumerSecret())
                        .build())
                .retrieve()
                .bodyToMono(SgisAuthResponse.class)
                .flatMap(response -> {
                    if (response.errCd() != 0 || response.result() == null) {
                        log.error("SGIS AccessToken 발급 실패 - errCd: {}, errMsg: {}", response.errCd(), response.errMsg());
                        return Mono.error(new ExternalApiException("SGIS",
                                "AccessToken 발급 실패: errCd=" + response.errCd() + ", errMsg=" + response.errMsg()));
                    }
                    SgisToken token = SgisToken.of(response.result().accessToken(), response.result().accessTimeout());
                    cachedToken.set(token);
                    log.info("SGIS AccessToken 발급 성공 - 만료 시각: {}", token.expiresAt());
                    return Mono.just(token);
                })
                .onErrorMap(ex -> !(ex instanceof ExternalApiException), ex -> {
                    log.error("SGIS AccessToken 발급 중 예외 발생", ex);
                    return new ExternalApiException("SGIS", "AccessToken 발급 중 예외: " + ex.getMessage(), ex);
                });
    }
}
