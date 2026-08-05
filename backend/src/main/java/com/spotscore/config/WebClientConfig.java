package com.spotscore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    // storeListInDong은 numOfRows=1000(페이지당 최대치)일 때 응답 본문이 WebClient
    // 기본 인메모리 버퍼 한도(256KB)를 넘는다 - 실제 호출 시
    // DataBufferLimitException으로 확인됨. 페이지당 데이터양에 여유를 두고 10MB로 올린다.
    private static final int STORE_ZONE_MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;

    @Bean
    public WebClient sgisWebClient(SgisProperties properties) {
        // sgisapi.kostat.go.kr는 실제로 sgisapi.mods.go.kr로 302 리다이렉트된다
        // (도메인 이관 확인됨). Reactor Netty 기본 설정은 리다이렉트를 따라가지 않으므로
        // 명시적으로 켜야 한다.
        HttpClient httpClient = HttpClient.create().followRedirect(true);
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public WebClient storeZoneWebClient(StoreZoneProperties properties) {
        // 공공데이터포털 serviceKey는 발급 시점에 이미 URL-인코딩된 값으로 내려온다
        // (예: '=' -> '%3D'). 기본 인코딩 모드(TEMPLATE_AND_VALUES)를 쓰면 그 안의 '%'가
        // 다시 인코딩되어 '%25'로 이중 인코딩되고 인증이 깨진다. serviceKey를 있는 그대로
        // 전달할 수 있도록 인코딩을 끄고, 나머지 파라미터(divId/key/numOfRows/pageNo)는
        // 인코딩이 필요 없는 값이라 안전하다.
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(properties.baseUrl());
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(STORE_ZONE_MAX_IN_MEMORY_SIZE_BYTES))
                .build();
        return WebClient.builder()
                .uriBuilderFactory(uriBuilderFactory)
                .exchangeStrategies(exchangeStrategies)
                .build();
    }
}
