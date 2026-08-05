package com.spotscore.config;

/**
 * 배치 대상 지역 1건 - SGIS adm_cd와 상권정보 adongCd 쌍. 실제 라이브 호출로 대조한
 * 결과 두 코드가 같은 지역이어도 다른 번호체계라 하나의 문자열로 양쪽에 그대로
 * 전달할 수 없다(BatchProperties, Region 참고) - 그래서 쌍으로 설정한다.
 */
public record TargetRegion(String sgisAdmCd, String adongCd) {

    public static TargetRegion parse(String raw) {
        String[] parts = raw.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    "잘못된 target-region 설정: '" + raw + "' (형식: sgisAdmCd:adongCd)");
        }
        return new TargetRegion(parts[0].trim(), parts[1].trim());
    }
}
