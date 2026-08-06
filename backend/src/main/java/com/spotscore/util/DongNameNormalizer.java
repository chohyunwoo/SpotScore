package com.spotscore.util;

/**
 * 행정동명 표기 정규화. SGIS와 상권정보가 복합 동명(예: 종로1·2·3·4가동)에
 * 서로 다른 구분자(가운뎃점 vs 마침표)를 쓰는 사례가 REGION 크로스워크
 * 재구축 진단에서 실제로 확인됐다(중랑구 "면목3·8동" vs "면목3.8동" 등).
 *
 * RegionCrosswalkRebuildService(구 단위 1회성 재구축)와 RegionCodeMappingValidator
 * (월간 배치 매 실행 시 검증) 양쪽이 서로 다른 규칙으로 정규화하면 한쪽만
 * 통과하고 한쪽은 막히는 불일치가 생기므로, 규칙을 여기 한 곳으로 모았다.
 */
public final class DongNameNormalizer {

    private DongNameNormalizer() {
    }

    public static String normalizeStrict(String name) {
        return name.replaceAll("\\s+", "");
    }

    public static String normalizeLoose(String name) {
        return normalizeStrict(name).replaceAll("[·ㆍ・.,]", "");
    }
}
