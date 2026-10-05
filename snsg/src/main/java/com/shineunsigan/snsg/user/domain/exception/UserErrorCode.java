package com.shineunsigan.snsg.user.domain.exception;

import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.common.exception.ErrorType;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    INVALID_NICKNAME(ErrorType.INVALID, "닉네임은 1자 이상 10자 이하여야 합니다."),
    INVALID_BIRTH_DATE(ErrorType.INVALID, "생년월일이 올바르지 않습니다."),
    DUPLICATED_NICKNAME(ErrorType.CONFLICT, "이미 사용 중인 닉네임입니다."),
    ALREADY_WITHDRAWN(ErrorType.CONFLICT, "이미 탈퇴한 사용자입니다."),
    USER_NOT_FOUND(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.");

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
