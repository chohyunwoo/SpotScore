package com.spotscore.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.spotscore.collector.dto.BoundaryCentroidDto;
import com.spotscore.collector.dto.SgisBoundaryResponse;
import com.spotscore.collector.geo.Epsg5179ToWgs84Transformer;
import com.spotscore.collector.geo.PolygonCentroidCalculator;
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
import java.util.ArrayList;
import java.util.List;

/**
 * SGIS boundary/hadmarea.geojson으로 행정구역 경계 폴리곤을 조회하고, 지도 마커용
 * centroid(WGS84 lat/lng)를 계산한다. 경계는 거의 바뀌지 않으므로 이 collector는
 * 월 1회 배치(MonthlyDataCollectionBatchJob)가 아니라 1회성 시딩
 * (RegionCoordinateSeedingService)에서만 쓰인다 - CLAUDE.md 확장성 원칙 1에 따라
 * 기존 SgisCollector를 고치지 않고 별도 구현체로 추가했다.
 */
@Component
public class SgisBoundaryCollector implements DataCollector<BoundaryCentroidDto> {

    private static final Logger log = LoggerFactory.getLogger(SgisBoundaryCollector.class);

    // SgisCollector와 동일한 기본값 정책 - 행정구역 경계도 SGIS의 연도별 데이터라
    // stat-year 설정을 그대로 재사용한다(새 설정 키를 따로 만들지 않음).
    private static final int DEFAULT_STAT_YEAR_OFFSET = 2;

    private final WebClient sgisWebClient;
    private final SgisAuthService sgisAuthService;
    private final SgisProperties properties;
    private final Epsg5179ToWgs84Transformer coordinateTransformer;

    public SgisBoundaryCollector(@Qualifier("sgisWebClient") WebClient sgisWebClient, SgisAuthService sgisAuthService,
                                  SgisProperties properties, Epsg5179ToWgs84Transformer coordinateTransformer) {
        this.sgisWebClient = sgisWebClient;
        this.sgisAuthService = sgisAuthService;
        this.properties = properties;
        this.coordinateTransformer = coordinateTransformer;
    }

    @Override
    public DataSourceType sourceType() {
        return DataSourceType.SGIS;
    }

    @Override
    public Flux<BoundaryCentroidDto> collect(String admCd) {
        return sgisAuthService.getValidAccessToken()
                .flatMapMany(accessToken -> fetchBoundary(admCd, accessToken));
    }

    private Flux<BoundaryCentroidDto> fetchBoundary(String admCd, String accessToken) {
        int year = resolveStatYear();
        log.debug("SGIS 경계 API 요청 시작 - adm_cd: {}, year: {}", admCd, year);
        return sgisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/boundary/hadmarea.geojson")
                        .queryParam("accessToken", accessToken)
                        .queryParam("adm_cd", admCd)
                        .queryParam("year", year)
                        .build())
                .retrieve()
                .bodyToMono(SgisBoundaryResponse.class)
                .doOnNext(response -> {
                    int count = response.features() == null ? 0 : response.features().size();
                    log.info("SGIS 경계 응답 수신 - adm_cd: {}, feature 건수: {}", admCd, count);
                })
                .flatMapMany(response -> {
                    if (response.errCd() != 0) {
                        log.error("SGIS 경계 응답 실패 - adm_cd: {}, errCd: {}, errMsg: {}",
                                admCd, response.errCd(), response.errMsg());
                        return Flux.error(new ExternalApiException("SGIS",
                                "errCd=" + response.errCd() + ", errMsg=" + response.errMsg()));
                    }
                    List<SgisBoundaryResponse.Feature> features =
                            response.features() == null ? List.of() : response.features();
                    return Flux.fromIterable(features).mapNotNull(feature -> toCentroidDto(admCd, feature));
                })
                .onErrorMap(ex -> !(ex instanceof ExternalApiException), ex -> {
                    log.error("SGIS 경계 API 호출 중 예외 발생 - adm_cd: {}", admCd, ex);
                    return new ExternalApiException("SGIS", "경계 조회 실패: " + ex.getMessage(), ex);
                });
    }

    private BoundaryCentroidDto toCentroidDto(String admCd, SgisBoundaryResponse.Feature feature) {
        SgisBoundaryResponse.Geometry geometry = feature.geometry();
        if (geometry == null || geometry.coordinates() == null) {
            log.warn("경계 centroid 계산 실패 - adm_cd: {}, 사유: geometry 없음", admCd);
            return null;
        }

        PolygonCentroidCalculator.Point projected;
        try {
            projected = switch (geometry.type()) {
                case "Polygon" -> PolygonCentroidCalculator.polygonCentroid(toPolygon(geometry.coordinates()));
                case "MultiPolygon" ->
                        PolygonCentroidCalculator.multiPolygonCentroid(toMultiPolygon(geometry.coordinates()));
                default -> throw new IllegalArgumentException("지원하지 않는 geometry.type: " + geometry.type());
            };
        } catch (RuntimeException ex) {
            log.warn("경계 centroid 계산 실패 - adm_cd: {}, geometry.type: {}, 사유: {}",
                    admCd, geometry.type(), ex.getMessage());
            return null;
        }

        double[] latLng = coordinateTransformer.toWgs84(projected.x(), projected.y());
        String admNm = feature.properties() == null ? null : feature.properties().admNm();
        return new BoundaryCentroidDto(admCd, admNm, latLng[0], latLng[1]);
    }

    private static double[] toPoint(JsonNode node) {
        return new double[] {node.get(0).asDouble(), node.get(1).asDouble()};
    }

    private static List<double[]> toRing(JsonNode ringNode) {
        List<double[]> ring = new ArrayList<>();
        for (JsonNode pointNode : ringNode) {
            ring.add(toPoint(pointNode));
        }
        return ring;
    }

    private static List<List<double[]>> toPolygon(JsonNode polygonNode) {
        List<List<double[]>> polygon = new ArrayList<>();
        for (JsonNode ringNode : polygonNode) {
            polygon.add(toRing(ringNode));
        }
        return polygon;
    }

    private static List<List<List<double[]>>> toMultiPolygon(JsonNode multiPolygonNode) {
        List<List<List<double[]>>> multiPolygon = new ArrayList<>();
        for (JsonNode polygonNode : multiPolygonNode) {
            multiPolygon.add(toPolygon(polygonNode));
        }
        return multiPolygon;
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
