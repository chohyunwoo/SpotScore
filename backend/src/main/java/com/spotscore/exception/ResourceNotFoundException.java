package com.spotscore.exception;

/**
 * 요청한 단건 리소스가 존재하지 않을 때 던진다 (예: score_cache에 없는
 * regionCode x industryCode 조합의 상세 조회). 목록 조회(랭킹/업종 목록)에서
 * 데이터가 없는 경우는 이 예외 대신 빈 배열을 200으로 반환한다 - "아직 배치가
 * 안 돈 것"과 "요청한 리소스가 아예 없는 것"을 프론트가 구분할 수 있어야 하기 때문.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
