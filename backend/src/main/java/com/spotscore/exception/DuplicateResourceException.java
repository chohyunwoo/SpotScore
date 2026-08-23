package com.spotscore.exception;

/**
 * 이미 존재하는 값과 충돌해 생성할 수 없을 때 던진다(예: 이미 가입된 이메일로
 * 회원가입 시도). GlobalExceptionHandler가 409 CONFLICT로 매핑한다.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
