package com.spotscore.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * SGIS 통계값 문자열을 숫자로 변환한다. SGIS는 통계값이 5 이하일 때 비공개(N/A)로
 * 내려주는 것으로 알려져 있으나 실제 마스킹 표기(빈 문자열/"*"/"-" 등)는 아직
 * 표본으로 확인되지 않았다 (CLAUDE.md "아직 결정되지 않은 사항"). 정책이 확정되기
 * 전까지는 파싱 실패 시 예외를 던지지 않고 WARN 로그 후 null(미상)로 남긴다.
 */
final class SgisValueParser {

    private static final Logger log = LoggerFactory.getLogger(SgisValueParser.class);

    private SgisValueParser() {
    }

    static Long parseLong(String regionCode, String fieldName, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            log.warn("N/A(비공개) 값 처리 - regionCode: {}, field: {}, rawValue: {}", regionCode, fieldName, rawValue);
            return null;
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            log.warn("N/A(비공개) 값 처리 - regionCode: {}, field: {}, rawValue: {} (숫자 변환 실패)",
                    regionCode, fieldName, rawValue);
            return null;
        }
    }

    static Double parseDouble(String regionCode, String fieldName, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            log.warn("N/A(비공개) 값 처리 - regionCode: {}, field: {}, rawValue: {}", regionCode, fieldName, rawValue);
            return null;
        }
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException ex) {
            log.warn("N/A(비공개) 값 처리 - regionCode: {}, field: {}, rawValue: {} (숫자 변환 실패)",
                    regionCode, fieldName, rawValue);
            return null;
        }
    }

    static BigDecimal parseBigDecimal(String regionCode, String fieldName, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            log.warn("N/A(비공개) 값 처리 - regionCode: {}, field: {}, rawValue: {}", regionCode, fieldName, rawValue);
            return null;
        }
        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException ex) {
            log.warn("N/A(비공개) 값 처리 - regionCode: {}, field: {}, rawValue: {} (숫자 변환 실패)",
                    regionCode, fieldName, rawValue);
            return null;
        }
    }
}
