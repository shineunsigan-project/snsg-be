package com.shineunsigan.snsg.admin.domain.exception;

import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.common.exception.ErrorType;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AdminErrorCode implements ErrorCode {

    INVALID_LOGIN_ID(ErrorType.INVALID, "아이디는 영문 소문자로 시작하는 4~20자의 영문 소문자·숫자만 쓸 수 있어요."),
    INVALID_ADMIN_PASSWORD(ErrorType.INVALID,
            "비밀번호는 영문과 숫자를 각각 1자 이상 넣어 8~64자로 써주세요. 공백·한글은 쓸 수 없어요."),
    INVALID_ADMIN_NAME(ErrorType.INVALID, "이름은 2~20자의 한글 또는 영문만 쓸 수 있어요."),
    INVALID_ADMIN_TEAM(ErrorType.INVALID, "소속팀을 선택해주세요."),
    INVALID_ADMIN_BIRTH_DATE(ErrorType.INVALID, "생년월일이 올바르지 않습니다."),
    DUPLICATED_LOGIN_ID(ErrorType.CONFLICT, "이미 사용 중인 아이디예요."),
    INVALID_ADMIN_CREDENTIALS(ErrorType.UNAUTHENTICATED, "아이디 또는 비밀번호가 올바르지 않아요."),
    ADMIN_LOGIN_LOCKED(ErrorType.UNAUTHENTICATED, "로그인에 여러 번 실패해 잠시 잠겼어요. 잠시 후 다시 시도해주세요."),
    ADMIN_PENDING_APPROVAL(ErrorType.FORBIDDEN, "가입 승인을 기다리고 있어요. 승인된 뒤 로그인할 수 있어요."),
    ADMIN_REJECTED(ErrorType.FORBIDDEN, "가입이 거절됐어요. 자세한 내용은 MASTER에게 문의해주세요."),
    ADMIN_SUSPENDED(ErrorType.FORBIDDEN, "이용이 중지된 계정이에요. 자세한 내용은 MASTER에게 문의해주세요."),
    INVALID_ADMIN_REFRESH_TOKEN(ErrorType.UNAUTHENTICATED, "다시 로그인해야 합니다."),
    /** 어드민 인증 필터 안에서만 쓴다. 응답으로는 나가지 않고 보호 경로는 공통 401이 된다. */
    INVALID_ADMIN_ACCESS_TOKEN(ErrorType.UNAUTHENTICATED, "로그인이 필요합니다."),
    /** MASTER 전용 경로를 MASTER가 아닌 어드민이 불렀다. 어드민 보안 체인의 접근 거부 처리기가 낸다. */
    MASTER_ONLY(ErrorType.FORBIDDEN, "MASTER만 할 수 있어요."),
    ADMIN_NOT_FOUND(ErrorType.NOT_FOUND, "찾을 수 없는 어드민이에요."),
    MASTER_NOT_MODIFIABLE(ErrorType.FORBIDDEN, "MASTER 계정은 승인·거절·이용 중지할 수 없어요."),
    ADMIN_STATUS_CONFLICT(ErrorType.CONFLICT, "지금 상태에서는 할 수 없어요. 목록을 새로 불러 상태를 확인해주세요."),
    INVALID_CURSOR(ErrorType.INVALID, "목록을 처음부터 다시 불러주세요.");

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
