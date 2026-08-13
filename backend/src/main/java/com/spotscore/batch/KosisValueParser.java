package com.spotscore.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KOSIS DT 필드(문자열 숫자)를 Long으로 변환한다. SgisValueParser와 동일한
 * "파싱 실패 시 예외 대신 WARN + null" 정책을 따르되, 로그 문구는 KOSIS 응답이라는
 * 출처를 명확히 한다(SGIS의 "5 이하 비공개(N/A)" 표기 관례와는 별개 사안).
 */
final class KosisValueParser {

    private static final Logger log = LoggerFactory.getLogger(KosisValueParser.class);

    private KosisValueParser() {
    }

    static Long parseLong(String regionCode, String fieldName, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            log.warn("KOSIS 값 처리 실패 - regionCode: {}, field: {}, rawValue: {}", regionCode, fieldName, rawValue);
            return null;
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            log.warn("KOSIS 값 처리 실패 - regionCode: {}, field: {}, rawValue: {} (숫자 변환 실패)",
                    regionCode, fieldName, rawValue);
            return null;
        }
    }
}
