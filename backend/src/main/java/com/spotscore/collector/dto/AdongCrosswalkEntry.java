package com.spotscore.collector.dto;

/**
 * 상권정보 API가 signguCd 단위 전체 스캔으로 실제로 알고 있는 (adongCd, adongNm)
 * 조합 1건. SGIS의 adm_cd와는 별개인 상권정보 자체 행정동 코드 레지스트리를
 * 그대로 옮긴 값이다 (RegionCrosswalkRebuildService 참고).
 */
public record AdongCrosswalkEntry(String adongCd, String adongNm) {
}
