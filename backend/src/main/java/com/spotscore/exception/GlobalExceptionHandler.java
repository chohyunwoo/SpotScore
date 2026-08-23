package com.spotscore.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    // 이 백엔드는 정적 파일을 서빙하지 않는 순수 API 서버라, 브라우저가 "/"나
    // "/favicon.ico"를 요청하면 스프링이 정적 리소스 핸들러에서 못 찾고 이 예외를
    // 던진다 - 실제 서버 오류가 아니라 단순 404이므로 catch-all 500 핸들러가 아니라
    // 여기서 먼저 잡아 404로 내려준다(Render 배포 후 실사용 중 발견, 2026-08).
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("정적 리소스 없음 - path: {}", request.getRequestURI());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "RESOURCE_NOT_FOUND", "요청한 경로를 찾을 수 없습니다.", request.getRequestURI());
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

    // @Valid 검증 실패(회원가입/로그인 요청 바디 등) - 첫 필드 에러 메시지를 사용자에게
    // 그대로 안내한다(어떤 필드가 왜 틀렸는지가 메시지에 담겨 있음).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : "요청 값이 올바르지 않습니다.";
        log.warn("요청 바디 검증 실패 - path: {}, message: {}", request.getRequestURI(), message);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", message, request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    // 요청 바디 자체가 파싱 불가(깨진 JSON/인코딩 등) - 서버 오류(500)가 아니라 잘못된
    // 요청(400)이므로 여기서 잡아 catch-all 500으로 새는 것을 막는다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("요청 바디를 읽을 수 없음 - path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "MALFORMED_REQUEST_BODY",
                "요청 본문을 해석할 수 없습니다. 형식을 확인해주세요.", request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    // 이미 존재하는 이메일로 회원가입 등 - 409로 구분해, 프론트가 "다시 시도"가 아니라
    // "다른 값을 쓰라"고 안내할 수 있게 한다.
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResourceException(
            DuplicateResourceException ex, HttpServletRequest request) {
        log.warn("리소스 충돌 - path: {}, message: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.CONFLICT.value(), "DUPLICATE_RESOURCE", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // 로그인 자격 증명 실패(BadCredentialsException 등). 세부 사유(계정 없음/비밀번호
    // 불일치)를 구분해 노출하면 계정 존재 여부가 새어나가므로 동일한 401 메시지로 통일한다.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("인증 실패 - path: {}, exceptionType: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "AUTHENTICATION_FAILED",
                "이메일 또는 비밀번호가 올바르지 않습니다.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
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
