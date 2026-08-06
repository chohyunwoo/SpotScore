package com.spotscore.discovery;

import com.spotscore.util.DongNameNormalizer;

/**
 * SGIS 행정동명과 상권정보 행정동명을 대조한다. 진단 과정에서 같은 동을 가리키는
 * 이름이 구분자만 다르게 표기된 사례가 실제로 확인됐다
 * (SGIS "면목3·8동" vs 상권정보 "면목3.8동" - 중랑구 실측). 1차로 공백만 제거해
 * 엄격 대조하고, 실패하면 구분자 문자(마침표/가운뎃점/아래아점/쉼표)까지 제거한
 * 완화 대조를 시도한다 - 두 대조 결과를 구분해 완화 대조로만 성립한 건은 별도
 * 표시해 사람이 확인할 수 있게 한다.
 *
 * 정규화 규칙 자체는 DongNameNormalizer로 추출해 RegionCodeMappingValidator와
 * 공유한다 - 규칙이 두 곳에 따로 있으면 한쪽만 고쳤을 때 불일치가 생긴다.
 */
final class DongNameMatcher {

    private DongNameMatcher() {
    }

    enum MatchKind {
        STRICT, LOOSE, NONE
    }

    static MatchKind match(String sgisName, String storeZoneName) {
        if (sgisName == null || storeZoneName == null) {
            return MatchKind.NONE;
        }
        if (DongNameNormalizer.normalizeStrict(sgisName).equals(DongNameNormalizer.normalizeStrict(storeZoneName))) {
            return MatchKind.STRICT;
        }
        if (DongNameNormalizer.normalizeLoose(sgisName).equals(DongNameNormalizer.normalizeLoose(storeZoneName))) {
            return MatchKind.LOOSE;
        }
        return MatchKind.NONE;
    }
}
