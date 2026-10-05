package com.shineunsigan.snsg.common.exception;

/**
 * 오류의 성격. HTTP 상태로 바꾸는 일은 presentation(common.web)에서만 한다.
 * domain이 전달 방식(HTTP)을 모르게 하기 위해 여기에는 HttpStatus를 두지 않는다.
 */
public enum ErrorType {

    /** 요청 값이나 상태가 규칙에 맞지 않다. */
    INVALID,

    /** 인증이 필요하거나 토큰이 유효하지 않다. */
    UNAUTHENTICATED,

    /** 인증은 됐지만 권한이 없다. */
    FORBIDDEN,

    /** 대상이 없다. */
    NOT_FOUND,

    /** 현재 상태와 충돌한다. 정원이 찼거나 이미 처리된 경우. */
    CONFLICT,

    /** 서버 오류. */
    INTERNAL
}
