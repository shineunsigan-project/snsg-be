package com.shineunsigan.snsg.schedule.domain.exception;

import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.common.exception.ErrorType;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ScheduleErrorCode implements ErrorCode {

    INVALID_CATEGORY(ErrorType.INVALID, "선택할 수 없는 활동이에요. 활동을 다시 골라주세요."),
    INVALID_PLACE(ErrorType.INVALID, "선택할 수 없는 장소예요. 장소를 다시 골라주세요."),
    INVALID_TITLE(ErrorType.INVALID, "활동명은 1~20자로 적어주세요."),
    INVALID_DESCRIPTION(ErrorType.INVALID, "10자 이상 적어주세요."),
    INVALID_START_AT(ErrorType.INVALID, "시작 일시는 지금부터 1시간 이후, 30일 이내에서 30분 단위로 골라주세요."),
    INVALID_CAPACITY(ErrorType.INVALID, "인원은 개설자를 포함해 4~24명으로 정해주세요."),
    SCHEDULE_TIME_CONFLICT(ErrorType.CONFLICT, "앞뒤 12시간 안에 참여하는 다른 일정이 있어요."),
    USER_NOT_ACTIVE(ErrorType.UNAUTHENTICATED, "이용할 수 없는 사용자예요. 다시 로그인해주세요.");

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
