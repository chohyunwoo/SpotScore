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
 * 테이블 - weight_key는 V4 마이그레이션에서 시드한 DEMAND_WEIGHT/SUPPLY_WEIGHT/
 * DEMAND_POPULATION_RATIO/DEMAND_HOUSEHOLD_RATIO 4개를 사용한다.
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

    protected ScoreWeightConfig() {
    }

    public ScoreWeightConfig(String weightKey, BigDecimal weightValue) {
        this.weightKey = weightKey;
        this.weightValue = weightValue;
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

    public void updateValue(BigDecimal weightValue) {
        this.weightValue = weightValue;
    }
}
