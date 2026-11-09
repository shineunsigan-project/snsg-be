package com.shineunsigan.snsg.schedule.domain.constants;

/**
 * 일정 상세에서 보는 사람이 지금 누를 수 있는 동작. FE는 이 값이 있을 때만 그 버튼을 활성으로 그린다.
 */
public enum ScheduleAction {

    /** 비로그인 [참여하기]. 로그인 화면으로 보낸다. */
    LOGIN_TO_JOIN,

    /** [참여하기]. */
    JOIN,

    /** [참여 취소]. */
    CANCEL_PARTICIPATION,

    /** 개설자의 [일정 취소]. */
    CANCEL_SCHEDULE
}
