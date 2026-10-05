package com.shineunsigan.snsg.common.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.CommonErrorCode;
import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.common.exception.ErrorType;

import lombok.extern.slf4j.Slf4j;

/**
 * 예외를 {@link ApiResponse} 봉투로 바꾼다.
 * {@link ErrorType} → HTTP 상태 변환은 이 클래스에서만 한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(toHttpStatus(errorCode.errorType()))
                .body(ApiResponse.error(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("처리하지 못한 예외", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(CommonErrorCode.INTERNAL_ERROR));
    }

    /**
     * 요청 형식 오류(400), 없는 경로(404), 지원하지 않는 메서드(405)·Content-Type(415) 등
     * Spring MVC가 던지는 예외. 상태 코드는 그대로 두고 봉투만 씌운다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        return ResponseEntity.status(statusCode)
                .body(ApiResponse.error(CommonErrorCode.INVALID_REQUEST));
    }

    /**
     * {@link ErrorResponse} 기반 예외도 같은 봉투로 내보낸다.
     */
    private HttpStatus toHttpStatus(ErrorType errorType) {
        return switch (errorType) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
