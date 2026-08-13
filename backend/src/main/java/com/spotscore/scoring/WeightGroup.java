package com.spotscore.scoring;

/**
 * 리프 가중치 계산식을 고를 때 쓰는 그룹. 업종별 AgeDirection(POSITIVE/NEGATIVE/
 * NEUTRAL, domain.AgeDirection)과는 다른 축이다 - POSITIVE/NEGATIVE 업종은 가중치
 * 계산식(연령적합도 축 포함 여부)을 공유하므로 이 열거형에서는 DIRECTIONAL 하나로
 * 합쳐진다. +/- 차이는 ageScore 계산 공식에서만 나타난다(CLAUDE.md 연령 구성
 * 지표 섹션).
 */
public enum WeightGroup {
    NEUTRAL,
    DIRECTIONAL
}
