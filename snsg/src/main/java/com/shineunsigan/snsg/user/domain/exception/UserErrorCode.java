package com.shineunsigan.snsg.user.domain.exception;

import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.common.exception.ErrorType;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    INVALID_NAME(ErrorType.INVALID, "이름은 2~20자의 한글 또는 영문만 쓸 수 있어요."),
    INVALID_NICKNAME(ErrorType.INVALID, "닉네임은 2~10자의 한글·영문·숫자·.·_만 쓸 수 있어요."),
    INVALID_EMAIL(ErrorType.INVALID, "이메일 형식이 올바르지 않아요."),
    INVALID_BIRTH_DATE(ErrorType.INVALID, "생년월일이 올바르지 않습니다."),
    UNDERAGE(ErrorType.INVALID, "만 18세 이상만 가입할 수 있어요."),
    DUPLICATED_NICKNAME(ErrorType.CONFLICT, "이미 사용 중인 닉네임이에요. 다른 닉네임을 입력해주세요."),
    ALREADY_WITHDRAWN(ErrorType.CONFLICT, "이미 탈퇴한 사용자입니다."),
    INVALID_PROFILE_IMAGE(ErrorType.INVALID, "JPG, PNG 형식의 사진만 올릴 수 있어요."),
    PROFILE_IMAGE_STORE_FAILED(ErrorType.INTERNAL, "이미지를 저장하지 못했어요. 잠시 후 다시 시도해주세요."),
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
