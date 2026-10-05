package com.shineunsigan.snsg.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 특정 도메인에 속하지 않는 오류. 모든 API에서 날 수 있다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_REQUEST(ErrorType.INVALID, "요청 형식이 올바르지 않습니다."),
    UNAUTHENTICATED(ErrorType.UNAUTHENTICATED, "로그인이 필요합니다."),
    INTERNAL_ERROR(ErrorType.INTERNAL, "서버 오류가 발생했습니다.");

    private final ErrorType errorType;
    private final String message;

    @Override
    public ErrorType errorType() {
        return errorType;
    }

    @Override
    public String message() {
        return message;
    }
}
