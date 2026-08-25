package com.spotscore.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SGIS와 상권정보가 복합 동명에 서로 다른 구분자(가운뎃점 vs 마침표)를 쓰는 실제
 * 사례(예: "면목3·8동" vs "면목3.8동", "종로1·2·3·4가동")를 정규화가 흡수하는지
 * 검증한다. 이 정규화 누락이 RegionCodeMappingValidator에서 복합 동명 7건을 표기
 * 차이만으로 불일치 처리하던 실제 회귀 버그였으므로(CLAUDE.md 해결 완료 이력),
 * 회귀 방지 테스트로 고정한다.
 */
class DongNameNormalizerTest {

    @Test
    void strictNormalizationOnlyRemovesWhitespace() {
        assertThat(DongNameNormalizer.normalizeStrict("서울특별시 강남구 역삼1동")).isEqualTo("서울특별시강남구역삼1동");
        // strict는 구분자를 보존한다 - loose와 구분되는 지점.
        assertThat(DongNameNormalizer.normalizeStrict("면목3·8동")).isEqualTo("면목3·8동");
    }

    @Test
    void looseNormalizationRemovesSeparatorsSoDotAndMiddleDotMatch() {
        // 핵심 회귀: 가운뎃점(·)과 마침표(.) 표기 차이가 정규화 후 동일해져야 한다.
        assertThat(DongNameNormalizer.normalizeLoose("면목3·8동"))
                .isEqualTo(DongNameNormalizer.normalizeLoose("면목3.8동"))
                .isEqualTo("면목38동");
    }

    @Test
    void looseNormalizationHandlesMultipleSeparatorsInComplexDongName() {
        assertThat(DongNameNormalizer.normalizeLoose("종로1·2·3·4가동"))
                .isEqualTo(DongNameNormalizer.normalizeLoose("종로1.2.3.4가동"))
                .isEqualTo("종로1234가동");
    }

    @Test
    void looseNormalizationRemovesHalfwidthMiddleDotAndComma() {
        // 규칙에 포함된 다른 구분자들(ㆍ, ・, ,)도 함께 제거된다.
        assertThat(DongNameNormalizer.normalizeLoose("면목3ㆍ8동")).isEqualTo("면목38동");
        assertThat(DongNameNormalizer.normalizeLoose("면목3・8동")).isEqualTo("면목38동");
        assertThat(DongNameNormalizer.normalizeLoose("가,나동")).isEqualTo("가나동");
    }

    @Test
    void looseNormalizationAlsoStripsWhitespace() {
        assertThat(DongNameNormalizer.normalizeLoose("서울특별시 강남구 역삼1동")).isEqualTo("서울특별시강남구역삼1동");
    }

    @Test
    void plainDongNameIsUnchangedUnderBothRules() {
        assertThat(DongNameNormalizer.normalizeStrict("역삼1동")).isEqualTo("역삼1동");
        assertThat(DongNameNormalizer.normalizeLoose("역삼1동")).isEqualTo("역삼1동");
    }
}
