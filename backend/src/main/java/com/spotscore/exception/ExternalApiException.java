package com.spotscore.exception;

/**
 * SGIS/상권정보 등 외부 공공데이터 API 호출 실패 시 collector 계층에서 던지는 예외.
 */
public class ExternalApiException extends RuntimeException {

    private final String source;

    public ExternalApiException(String source, String message) {
        super(message);
        this.source = source;
    }

    public ExternalApiException(String source, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    public String getSource() {
        return source;
    }
}
