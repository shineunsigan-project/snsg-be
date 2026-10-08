package com.shineunsigan.snsg.auth.domain.exception;

import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.common.exception.ErrorType;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_ACCESS_TOKEN(ErrorType.UNAUTHENTICATED, "로그인이 필요합니다."),
    INVALID_REFRESH_TOKEN(ErrorType.UNAUTHENTICATED, "다시 로그인해야 합니다."),
    INVALID_SIGNUP_TOKEN(ErrorType.UNAUTHENTICATED, "가입 정보가 만료되었습니다. 다시 로그인해주세요."),
    UNSUPPORTED_SOCIAL_PROVIDER(ErrorType.INVALID, "지원하지 않는 소셜 로그인입니다."),
    INVALID_WEBHOOK_SENDER(ErrorType.FORBIDDEN, "허용되지 않은 요청입니다."),
    INVALID_PROFILE_IMAGE_URL(ErrorType.INVALID, "프로필 사진은 업로드한 사진만 쓸 수 있어요."),
    INVALID_SOCIAL_CODE(ErrorType.UNAUTHENTICATED, "소셜 로그인에 실패했습니다. 다시 시도해주세요."),
    SOCIAL_USER_FETCH_FAILED(ErrorType.INTERNAL, "소셜 사용자 정보를 가져오지 못했습니다.");

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
