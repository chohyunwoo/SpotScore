package com.spotscore.collector;

import reactor.core.publisher.Flux;

/**
 * 외부 공공데이터 API로부터 원자료를 수집하는 공통 인터페이스.
 * 새 데이터 소스를 추가할 때는 기존 구현체를 수정하지 않고 이 인터페이스의
 * 구현체만 추가한다 (CLAUDE.md 확장성 설계 원칙 1).
 */
public interface DataCollector<T> {

    /**
     * @param regionCode 행정표준코드 (SGIS adm_cd 또는 상권정보 adongCd).
     *                    두 코드 체계의 자릿수/매핑은 아직 표본 검증 전이다.
     */
    Flux<T> collect(String regionCode);

    DataSourceType sourceType();
}
