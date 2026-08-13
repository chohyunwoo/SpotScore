package com.spotscore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 하이브리드 AHP 쌍대비교 값. 점수 계산식에 숫자를 하드코딩하지 않기 위한 설정
 * 테이블 - weight_key는 V4/V11 마이그레이션에서 시드한 DEMAND_WEIGHT/SUPPLY_WEIGHT/
 * POPULATION_RATIO/HOUSEHOLD_RATIO/CORE_WEIGHT/AGE_WEIGHT를 사용한다.
 * weightGroup은 이 행이 NEUTRAL/DIRECTIONAL 중 어디서 쓰이는지 나타내는
 * 메타데이터일 뿐 조회 키가 아니다(weight_key 자체가 전역으로 유일) - COMMON은
 * 두 그룹이 공유, DIRECTIONAL은 DIRECTIONAL 그룹 전용(V11 참고).
 */
@Entity
@Table(name = "score_weight_config")
public class ScoreWeightConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "weight_key", nullable = false, unique = true, length = 50)
    private String weightKey;

    @Column(name = "weight_value", nullable = false)
    private BigDecimal weightValue;

    @Column(name = "weight_group", length = 20)
    private String weightGroup;

    protected ScoreWeightConfig() {
    }

    public ScoreWeightConfig(String weightKey, BigDecimal weightValue) {
        this(weightKey, weightValue, null);
    }

    public ScoreWeightConfig(String weightKey, BigDecimal weightValue, String weightGroup) {
        this.weightKey = weightKey;
        this.weightValue = weightValue;
        this.weightGroup = weightGroup;
    }

    public Long getConfigId() {
        return configId;
    }

    public String getWeightKey() {
        return weightKey;
    }

    public BigDecimal getWeightValue() {
        return weightValue;
    }

    public String getWeightGroup() {
        return weightGroup;
    }

    public void updateValue(BigDecimal weightValue) {
        this.weightValue = weightValue;
    }
}
