package com.shineunsigan.snsg.common.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반. 전역 예외 처리에서 응답으로 변환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }
}
