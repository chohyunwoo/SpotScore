package com.spotscore.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApiException(ExternalApiException ex, HttpServletRequest request) {
        log.error("외부 API 호출 실패 - source: {}, path: {}", ex.getSource(), request.getRequestURI(), ex);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_GATEWAY.value(), "EXTERNAL_API_ERROR", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("잘못된 요청 파라미터 - path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "INVALID_ARGUMENT", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("리소스를 찾을 수 없음 - path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "RESOURCE_NOT_FOUND", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("필수 요청 파라미터 누락 - path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "MISSING_PARAMETER", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> handleDataAccessException(Exception ex, HttpServletRequest request) {
        log.error("쿼리/커넥션 실패 - path: {}, exceptionType: {}", request.getRequestURI(), ex.getClass().getSimpleName(), ex);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "DATABASE_ERROR", "데이터베이스 처리 중 오류가 발생했습니다.", request.getRequestURI());
        return ResponseEntity.internalServerError().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("예상치 못한 예외 발생 - path: {}, exceptionType: {}", request.getRequestURI(), ex.getClass().getSimpleName(), ex);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.", request.getRequestURI());
        return ResponseEntity.internalServerError().body(body);
    }
}
