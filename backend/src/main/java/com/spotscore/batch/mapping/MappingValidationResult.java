package com.spotscore.batch.mapping;

import com.spotscore.collector.dto.SgisPopulationDto;
import com.spotscore.collector.dto.StoreItemDto;
import com.spotscore.config.TargetRegion;

import java.util.List;

/**
 * SGIS adm_cd ↔ 상권정보 adongCd 대조 결과. valid=false인 경우 배치는 해당
 * 지역을 WARN 로그와 함께 건너뛴다 (CLAUDE.md 로깅 가이드 - 행정구역 코드 매핑,
 * 프로젝트 지시사항 제약: 매핑 실패 시 예외를 던지지 않는다).
 */
public record MappingValidationResult(
        TargetRegion target,
        boolean valid,
        String reason,
        List<SgisPopulationDto> populationRows,
        List<StoreItemDto> storeItems
) {

    public static MappingValidationResult failure(TargetRegion target, String reason) {
        return new MappingValidationResult(target, false, reason, List.of(), List.of());
    }

    public static MappingValidationResult success(TargetRegion target, List<SgisPopulationDto> populationRows,
                                                   List<StoreItemDto> storeItems) {
        return new MappingValidationResult(target, true, null, populationRows, storeItems);
    }
}
