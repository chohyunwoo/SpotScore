package com.spotscore.domain;

/**
 * 업종별 "20~39세 비중이 높을수록 유리한 업종인지" 방향성. NEUTRAL 업종은
 * ageScore를 계산하지 않는다(가중치 반영 대상에서도 제외 - CLAUDE.md 연령 구성
 * 지표 섹션).
 */
public enum AgeDirection {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}
