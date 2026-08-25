package com.spotscore.batch.mapping;

import com.spotscore.collector.SgisCollector;
import com.spotscore.collector.StoreZoneCollector;
import com.spotscore.collector.dto.SgisPopulationDto;
import com.spotscore.collector.dto.StoreItemDto;
import com.spotscore.config.TargetRegion;
import com.spotscore.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SGIS adm_cd ↔ 상권정보 adongCd 정합성 검증의 판정 분기를 콜렉터 목킹으로 확인한다.
 * 핵심 회귀 방지: 복합 동명의 구분자 차이(가운뎃점 vs 마침표)를 DongNameNormalizer로
 * 흡수해 "성공"으로 판정하는지(REGION 크로스워크 재구축 진단에서 7건이 표기 차이만으로
 * 불일치 처리되던 버그, CLAUDE.md 해결 완료 이력)와, 실제 다른 지역이면 정확히
 * 실패(REASON)로 걸러내는지 둘 다 고정한다.
 */
class RegionCodeMappingValidatorTest {

    private static final String SGIS_ADM_CD = "11060810";
    private static final String ADONG_CD = "11260680";

    private SgisCollector sgisCollector;
    private StoreZoneCollector storeZoneCollector;
    private RegionCodeMappingValidator validator;
    private TargetRegion target;

    @BeforeEach
    void setUp() {
        sgisCollector = mock(SgisCollector.class);
        storeZoneCollector = mock(StoreZoneCollector.class);
        validator = new RegionCodeMappingValidator(sgisCollector, storeZoneCollector);
        target = new TargetRegion(SGIS_ADM_CD, ADONG_CD);
    }

    @Test
    void failsWhenSgisReturnsEmpty() {
        when(sgisCollector.collect(SGIS_ADM_CD)).thenReturn(Flux.empty());

        MappingValidationResult result = validator.validate(target);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("SGIS_EMPTY_RESULT");
    }

    @Test
    void failsWhenSgisCallThrows() {
        when(sgisCollector.collect(SGIS_ADM_CD))
                .thenReturn(Flux.error(new ExternalApiException("SGIS", "boom")));

        MappingValidationResult result = validator.validate(target);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("SGIS_CALL_FAILED");
    }

    @Test
    void failsWhenStoreZoneEchoesDifferentAdongCd() {
        when(sgisCollector.collect(SGIS_ADM_CD))
                .thenReturn(Flux.just(population("서울특별시 중랑구 면목3.8동")));
        // 상권정보가 요청한 adongCd와 다른 코드를 담아 응답 -> 다른 지역 데이터가 돌아온 것.
        when(storeZoneCollector.collect(ADONG_CD))
                .thenReturn(Flux.just(store("99999999", "면목3·8동")));

        MappingValidationResult result = validator.validate(target);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("ADONG_CD_ECHO_MISMATCH");
    }

    @Test
    void failsWhenRegionNamesDoNotMatch() {
        when(sgisCollector.collect(SGIS_ADM_CD))
                .thenReturn(Flux.just(population("서울특별시 중랑구 면목3.8동")));
        // adongCd는 일치하지만 이름이 전혀 다른 지역 -> 실제로는 다른 지역을 가리킴.
        when(storeZoneCollector.collect(ADONG_CD))
                .thenReturn(Flux.just(store(ADONG_CD, "역삼1동")));

        MappingValidationResult result = validator.validate(target);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("REGION_NAME_MISMATCH");
    }

    @Test
    void succeedsDespiteSeparatorDifferenceInComplexDongName() {
        // 핵심 회귀: SGIS는 마침표("면목3.8동"), 상권정보는 가운뎃점("면목3·8동")을 쓴다.
        // 원문 그대로 contains()하면 불일치지만, loose 정규화로 흡수되어 성공해야 한다.
        when(sgisCollector.collect(SGIS_ADM_CD))
                .thenReturn(Flux.just(population("서울특별시 중랑구 면목3.8동")));
        when(storeZoneCollector.collect(ADONG_CD))
                .thenReturn(Flux.just(store(ADONG_CD, "면목3·8동")));

        MappingValidationResult result = validator.validate(target);

        assertThat(result.valid()).isTrue();
        assertThat(result.reason()).isNull();
        assertThat(result.populationRows()).hasSize(1);
        assertThat(result.storeItems()).hasSize(1);
    }

    @Test
    void succeedsWhenStoreZoneReturnsZeroItems() {
        // 상권정보 0건은 실패로 단정하지 않는다 - 인구 통계만으로 정합성 확인, 업소 없음은 정상 케이스.
        when(sgisCollector.collect(SGIS_ADM_CD))
                .thenReturn(Flux.just(population("서울특별시 중랑구 면목3.8동")));
        when(storeZoneCollector.collect(ADONG_CD)).thenReturn(Flux.empty());

        MappingValidationResult result = validator.validate(target);

        assertThat(result.valid()).isTrue();
        assertThat(result.populationRows()).hasSize(1);
        assertThat(result.storeItems()).isEmpty();
    }

    private static SgisPopulationDto population(String admNm) {
        return new SgisPopulationDto(SGIS_ADM_CD, admNm, null, null, null, null);
    }

    private static StoreItemDto store(String adongCode, String adongName) {
        return new StoreItemDto("bizId", "가게", null, null, null, null,
                adongCode, adongName, null, null, null, null);
    }
}
