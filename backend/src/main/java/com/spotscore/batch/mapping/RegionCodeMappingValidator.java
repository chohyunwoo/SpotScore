package com.spotscore.batch.mapping;

import com.spotscore.collector.SgisCollector;
import com.spotscore.collector.StoreZoneCollector;
import com.spotscore.collector.dto.SgisPopulationDto;
import com.spotscore.collector.dto.StoreItemDto;
import com.spotscore.config.TargetRegion;
import com.spotscore.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SGIS adm_cd와 상권정보 adongCd가 같은 지역을 가리키는지 실제 응답으로 대조한다.
 *
 * 실제 두 API를 라이브로 호출해 확인한 결과, adm_cd와 adongCd는 같은 지역이어도
 * 서로 다른 번호체계다 (예: 강남구 역삼1동 - SGIS adm_cd=11230640, 상권정보
 * adongCd=11680640; 시군구 5자리 prefix가 11230 vs 11680으로 다름). 그래서 같은
 * 문자열을 양쪽에 넘겨 코드가 같은지 비교하는 방식은 성립하지 않는다 - 대신 두
 * 코드가 사전에 "같은 지역을 가리키는 쌍"으로 설정(TargetRegion)돼 있다는 전제
 * 하에, 각 API가 실제로 유효한 데이터를 돌려주는지 + 응답에 담긴 지역명이 서로
 * 대응하는지(부분 문자열 포함 여부)를 대조해 정합성을 검증한다.
 *
 * 판정 기준:
 * - SGIS 응답이 비어있으면 매핑 실패 (인구 통계 없이는 점수 산출 불가).
 * - 상권정보 응답 items가 비어있는 것은 "0건"일 수도 있어 실패로 단정하지 않는다.
 * - 상권정보 items가 있는데 그 안의 adongCd가 요청한 adongCd와 다르면 실패 (API가
 *   요청과 다른 지역을 돌려준 것이므로).
 * - 상권정보 items가 있고 SGIS 응답의 adm_nm이 있으면, adm_nm에 상권정보의 adongNm이
 *   부분 문자열로 포함되는지 확인한다 (예: "서울특별시강남구역삼1동"에 "역삼1동" 포함).
 *   포함되지 않으면 두 코드가 실제로는 다른 지역을 가리키는 것으로 보고 실패 처리.
 */
@Component
public class RegionCodeMappingValidator {

    private static final Logger log = LoggerFactory.getLogger(RegionCodeMappingValidator.class);

    private final SgisCollector sgisCollector;
    private final StoreZoneCollector storeZoneCollector;

    public RegionCodeMappingValidator(SgisCollector sgisCollector, StoreZoneCollector storeZoneCollector) {
        this.sgisCollector = sgisCollector;
        this.storeZoneCollector = storeZoneCollector;
    }

    public MappingValidationResult validate(TargetRegion target) {
        List<SgisPopulationDto> populationRows;
        try {
            populationRows = sgisCollector.collect(target.sgisAdmCd()).collectList().block();
        } catch (ExternalApiException ex) {
            log.warn("행정구역 코드 매핑 실패 - sgisAdmCd: {}, adongCd: {}, 사유: SGIS API 호출 실패 ({})",
                    target.sgisAdmCd(), target.adongCd(), ex.getMessage());
            return MappingValidationResult.failure(target, "SGIS_CALL_FAILED");
        }

        if (populationRows == null || populationRows.isEmpty()) {
            log.warn("행정구역 코드 매핑 실패 - sgisAdmCd: {}, 사유: SGIS 인구 통계 응답 없음", target.sgisAdmCd());
            return MappingValidationResult.failure(target, "SGIS_EMPTY_RESULT");
        }

        List<StoreItemDto> storeItems;
        try {
            storeItems = storeZoneCollector.collect(target.adongCd()).collectList().block();
        } catch (ExternalApiException ex) {
            log.warn("행정구역 코드 매핑 실패 - adongCd: {}, 사유: 상권정보 API 호출 실패 ({})",
                    target.adongCd(), ex.getMessage());
            return MappingValidationResult.failure(target, "STORE_ZONE_CALL_FAILED");
        }

        if (storeItems == null) {
            storeItems = List.of();
        }

        if (!storeItems.isEmpty()) {
            StoreItemDto sample = storeItems.get(0);
            if (sample.adongCode() != null && !sample.adongCode().equals(target.adongCd())) {
                log.warn("행정구역 코드 매핑 불일치 - 요청 adongCd: {}, 상권정보 응답 adongCd: {} (다른 지역 데이터가 돌아옴)",
                        target.adongCd(), sample.adongCode());
                return MappingValidationResult.failure(target, "ADONG_CD_ECHO_MISMATCH");
            }

            String admNm = populationRows.get(0).admNm();
            if (admNm != null && sample.adongName() != null && !admNm.contains(sample.adongName())) {
                log.warn("행정구역 코드 매핑 불일치 - sgisAdmCd: {}(adm_nm: {}) vs adongCd: {}(adongNm: {}) - 서로 다른 지역을 " +
                                "가리키는 것으로 보임(TargetRegion 설정값 재확인 필요)",
                        target.sgisAdmCd(), admNm, target.adongCd(), sample.adongName());
                return MappingValidationResult.failure(target, "REGION_NAME_MISMATCH");
            }
        } else {
            log.info("상권정보 응답 0건 - adongCd: {} (매핑 정합성은 인구 통계만으로 확인됨, 업소 없음은 정상 케이스일 수 있음)",
                    target.adongCd());
        }

        log.info("행정구역 코드 매핑 검증 성공 - sgisAdmCd: {}, adongCd: {}, 인구 통계 {}건, 상권 업소 {}건",
                target.sgisAdmCd(), target.adongCd(), populationRows.size(), storeItems.size());
        return MappingValidationResult.success(target, populationRows, storeItems);
    }
}
