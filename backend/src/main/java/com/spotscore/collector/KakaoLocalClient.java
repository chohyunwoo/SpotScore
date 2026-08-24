package com.spotscore.collector;

import com.spotscore.collector.dto.KakaoKeywordSearchResponse;
import com.spotscore.config.KakaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Kakao Local 키워드 검색으로 가게명(+좌표)에 해당하는 등록 장소의 카카오맵 상세 URL을
 * 찾는다. 좌표가 있으면 그 근처를 거리순으로 검색해 동명이인 상호 중 가장 가까운
 * 곳을 고른다. 키 미설정/결과 없음/호출 실패는 모두 null을 반환해, 프론트가 이름 검색
 * 링크로 폴백하게 한다(이슈 #34).
 */
@Component
public class KakaoLocalClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClient.class);
    // 좌표 근처 우선 검색 반경(m). 상권정보 좌표와 카카오 장소 좌표 간 오차를 감안한 여유값.
    private static final int SEARCH_RADIUS_METERS = 500;

    private final WebClient kakaoWebClient;
    private final KakaoProperties properties;

    public KakaoLocalClient(@Qualifier("kakaoWebClient") WebClient kakaoWebClient, KakaoProperties properties) {
        this.kakaoWebClient = kakaoWebClient;
        this.properties = properties;
    }

    public String findPlaceUrl(String name, Double lon, Double lat) {
        if (!properties.isConfigured()) {
            log.debug("KAKAO_REST_API_KEY 미설정 - 장소 링크 조회 스킵(이름 검색으로 폴백)");
            return null;
        }
        if (name == null || name.isBlank()) {
            return null;
        }

        try {
            KakaoKeywordSearchResponse response = kakaoWebClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/v2/local/search/keyword.json")
                                .queryParam("query", name)
                                .queryParam("size", 5);
                        // 좌표가 있으면 그 지점 근처를 거리순으로 우선 검색한다(x=경도, y=위도).
                        if (lon != null && lat != null) {
                            uriBuilder.queryParam("x", lon)
                                    .queryParam("y", lat)
                                    .queryParam("radius", SEARCH_RADIUS_METERS)
                                    .queryParam("sort", "distance");
                        }
                        return uriBuilder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                    .retrieve()
                    .bodyToMono(KakaoKeywordSearchResponse.class)
                    .block();

            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                log.info("카카오 장소 검색 결과 없음 - name: {}", name);
                return null;
            }
            return response.documents().get(0).placeUrl();
        } catch (Exception ex) {
            // 부가 기능이라 실패해도 예외를 위로 던지지 않고 폴백(null)한다.
            log.warn("카카오 장소 검색 실패 - name: {} ({})", name, ex.getMessage());
            return null;
        }
    }
}
