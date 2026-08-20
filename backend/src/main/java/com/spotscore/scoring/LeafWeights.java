package com.spotscore.scoring;

/**
 * AHP 쌍대비교로부터 계산된 리프 가중치. NEUTRAL 그룹은 인구 규모/가구 구조/
 * 경쟁 여유도 3개만 쓰고(ageWeight=null, 합=1), DIRECTIONAL 그룹은 연령적합도가
 * 추가되어 4개 리프의 합이 1이 되도록 ScoreWeightService에서 계산된다.
 */
public record LeafWeights(double populationWeight, double householdWeight, double competitionWeight, Double ageWeight) {

    public boolean hasAgeWeight() {
        return ageWeight != null;
    }
}
