package com.spotscore.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotscore.collector.dto.KosisAgeStatItemDto;
import com.spotscore.config.KosisProperties;
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
 * KOSIS(통계청) Param/statisticsParameterData.do, 테이블 DT_1B04005N(행정구역
 * (읍면동)별/5세별 주민등록인구)로 20~39세 인구와 KOSIS 자체 총인구를 수집한다.
 *
 * 지역코드 변환: REGION.regionCode(=상권정보 adongCd 기준 표준 행정표준코드) 뒤에
 * "00"을 붙인 10자리 코드가 KOSIS objL1이다 - 실제 라이브 호출로 검증 완료
 * (2026-08-13, 역삼1동 regionCode "11680640" + "00" = "1168064000" → KOSIS가
 * "역삼1동"으로 정확히 응답, 총인구 34216명/20-24세 1676명 등 확인). SGIS의
 * sgisAdmCd는 KOSIS 코드와 별개 체계이므로(SGIS adm_cd "11230640"은 실제로는
 * KOSIS/표준 코드 체계에서 "동대문구" 소속 코드로 확인됨 - Region.regionCode/
 * sgisAdmCd 필드 주석 참고) 여기에 절대 쓰면 안 된다.
 */
@Component
public class KosisAgeCollector implements DataCollector<KosisAgeStatItemDto> {

    private static final Logger log = LoggerFactory.getLogger(KosisAgeCollector.class);

    private static final int DEFAULT_STAT_YEAR_OFFSET = 2;
    private static final String REGION_CODE_SUFFIX = "00";
    private static final String TABLE_ID = "DT_1B04005N";
    private static final String ORG_ID = "101";
    private static final String ITEM_ID = "T2";
    // C2 코드: 0=계(총인구), 25/30/35/40=20-24/25-29/30-34/35-39세 5세 구간
    // (실제 라이브 호출로 검증 완료) - "+"로 묶어 1회 호출로 총인구+4개 구간을 함께 받는다.
    private static final String OBJ_L2 = "0+25+30+35+40";

    private final WebClient kosisWebClient;
    private final KosisProperties properties;
    private final ObjectMapper objectMapper;

    public KosisAgeCollector(@Qualifier("kosisWebClient") WebClient kosisWebClient, KosisProperties properties,
                              ObjectMapper objectMapper) {
        this.kosisWebClient = kosisWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public DataSourceType sourceType() {
        return DataSourceType.KOSIS;
    }

    @Override
    public Flux<KosisAgeStatItemDto> collect(String regionCode) {
        String kosisRegionCode = regionCode + REGION_CODE_SUFFIX;
        int statYear = resolveStatYear();
        log.debug("KOSIS 연령별 인구 API 요청 시작 - regionCode: {}, kosisRegionCode: {}, year: {}",
                regionCode, kosisRegionCode, statYear);

        return kosisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/Param/statisticsParameterData.do")
                        .queryParam("method", "getList")
                        .queryParam("apiKey", properties.apiKey())
                        .queryParam("itmId", ITEM_ID)
                        .queryParam("objL1", kosisRegionCode)
                        .queryParam("objL2", OBJ_L2)
                        .queryParam("format", "json")
                        .queryParam("jsonVD", "Y")
                        .queryParam("prdSe", "Y")
                        .queryParam("startPrdDe", statYear)
                        .queryParam("endPrdDe", statYear)
                        .queryParam("orgId", ORG_ID)
                        .queryParam("tblId", TABLE_ID)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> log.debug("KOSIS 연령별 인구 응답 원문 수신 - regionCode: {}", regionCode))
                .flatMapMany(body -> parseResponse(regionCode, body))
                .onErrorMap(ex -> !(ex instanceof ExternalApiException), ex -> {
                    log.error("KOSIS 연령별 인구 API 호출 중 예외 발생 - regionCode: {}", regionCode, ex);
                    return new ExternalApiException("KOSIS", "연령별 인구 조회 실패: " + ex.getMessage(), ex);
                });
    }

    // KOSIS 응답은 SGIS/StoreZone처럼 고정된 envelope(errCd/resultCode 필드가 있는
    // 객체)가 아니라, 성공 시 배열 / 실패 시 {"err":..,"errMsg":..} 객체로 최상위
    // 타입 자체가 달라진다(실제 라이브 호출로 확인) - 그래서 DTO로 바로
    // bodyToMono하지 않고 JsonNode로 먼저 성공/실패를 분기한다.
    private Flux<KosisAgeStatItemDto> parseResponse(String regionCode, String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception ex) {
            log.error("KOSIS 연령별 인구 응답 파싱 실패 - regionCode: {}, body: {}", regionCode, body, ex);
            return Flux.error(new ExternalApiException("KOSIS", "응답 파싱 실패: " + ex.getMessage(), ex));
        }

        if (root.isObject() && root.has("err")) {
            String errCode = root.path("err").asText();
            String errMsg = root.path("errMsg").asText();
            log.error("KOSIS 연령별 인구 응답 실패 - regionCode: {}, err: {}, errMsg: {}", regionCode, errCode, errMsg);
            return Flux.error(new ExternalApiException("KOSIS", "err=" + errCode + ", errMsg=" + errMsg));
        }

        if (!root.isArray()) {
            log.error("KOSIS 연령별 인구 응답 형식 예상과 다름 - regionCode: {}, body: {}", regionCode, body);
            return Flux.error(new ExternalApiException("KOSIS", "예상치 못한 응답 형식"));
        }

        List<KosisAgeStatItemDto> items = new ArrayList<>();
        root.forEach(node -> items.add(new KosisAgeStatItemDto(
                node.path("C1").asText(null),
                node.path("C2").asText(null),
                node.path("DT").asText(null))));

        log.info("KOSIS 연령별 인구 응답 수신 - regionCode: {}, 건수: {}", regionCode, items.size());
        return Flux.fromIterable(items);
    }

    private int resolveStatYear() {
        if (properties.statYear() != null) {
            return properties.statYear();
        }
        int fallback = Year.now().getValue() - DEFAULT_STAT_YEAR_OFFSET;
        log.warn("spotscore.kosis.stat-year 미설정 - 기본값 {}년 사용 (실제 발행 연도 확인 후 명시적으로 설정 권장)", fallback);
        return fallback;
    }
}
