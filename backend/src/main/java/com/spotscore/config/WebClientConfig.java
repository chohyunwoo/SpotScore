package com.spotscore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    // storeListInDong은 numOfRows=1000(페이지당 최대치)일 때 응답 본문이 WebClient
    // 기본 인메모리 버퍼 한도(256KB)를 넘는다 - 실제 호출 시
    // DataBufferLimitException으로 확인됨. 페이지당 데이터양에 여유를 두고 10MB로 올린다.
    private static final int STORE_ZONE_MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;

    @Bean
    public WebClient sgisWebClient(SgisProperties properties) {
        // base-url이 최종 도메인(sgisapi.mods.go.kr)을 직접 가리키므로 이 리다이렉트는
        // 정상 경로에서는 타지 않는다 - kostat.go.kr 도메인이 다시 쓰이는 경우를 대비한 안전장치.
        HttpClient httpClient = HttpClient.create().followRedirect(true);
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public WebClient kosisWebClient(KosisProperties properties) {
        // KOSIS apiKey는 StoreZone의 serviceKey와 달리 미리 인코딩된 값이 아니라 원문
        // base64 값('=' 패딩 포함)이 그대로 내려온다(.env 확인) - 기본 인코딩 모드로
        // '=' -> '%3D' 1회 인코딩되는 것이 실제로 맞는 동작이다(라이브 호출로 검증 완료,
        // StoreZoneProperties처럼 인코딩을 끄면 오히려 실패한다).
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
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

    @Bean
    public WebClient groqWebClient(GroqProperties properties) {
        // Groq(OpenAI 호환 엔드포인트)는 SGIS처럼 별도 accessToken 발급 단계가 없다 -
        // 매 요청에 동일한 Authorization 헤더만 필요하므로 SgisAuthService 같은 토큰
        // 캐싱 컴포넌트 없이 빈 생성 시점에 헤더를 고정한다. responseTimeout은
        // tool-calling 루프가 여러 번 왕복할 수 있어 기본 타임아웃보다 넉넉하게
        // 설정값으로 분리한다.
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
