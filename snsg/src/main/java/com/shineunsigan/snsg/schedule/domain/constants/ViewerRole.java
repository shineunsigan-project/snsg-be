package com.shineunsigan.snsg.schedule.domain.constants;

/**
 * 일정 상세를 보는 사람의 역할. 저장하지 않고 상세를 부를 때마다 판정한다.
 */
public enum ViewerRole {

    /** 로그인하지 않았다. */
    GUEST,

    /** 일정을 연 사람. */
    HOST,

    /** 멤버로 확정 참여했다. */
    PARTICIPANT,

    /** 로그인했지만 확정 참여하지 않았다. 참여를 취소한 사람도 여기다. */
    NON_PARTICIPANT
}
