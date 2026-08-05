package com.spotscore.scoring;

/**
 * AHP 2단계 쌍대비교로부터 계산된 리프 3개(인구 규모/가구 구조/경쟁 밀집도)의
 * 최종 가중치. populationWeight + householdWeight + competitionWeight = 1이 되도록
 * ScoreWeightService에서 계산된다.
 */
record LeafWeights(double populationWeight, double householdWeight, double competitionWeight) {
}
