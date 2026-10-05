package com.shineunsigan.snsg.common.exception;

/**
 * 도메인마다 enum으로 구현한다. enum 이름이 곧 클라이언트가 분기에 쓰는 code다.
 * common은 어떤 도메인 코드가 있는지 알지 못한다.
 */
public interface ErrorCode {

    /** enum 이름. 응답의 code 필드가 된다. */
    String name();

    ErrorType errorType();

    String message();
}
