package com.shineunsigan.snsg.common.entity;

/**
 * 사용자가 아닌 주체가 데이터를 바꿀 때 감사 컬럼에 들어가는 값.
 */
public final class Auditor {

    /** 인증 주체 없이 일어나는 변경. 회원가입 등. */
    public static final String SYSTEM = "SYSTEM";

    /** 스케줄러가 일으키는 변경. 일정 상태 전환 등. */
    public static final String SYSTEM_SCHEDULER = "SYSTEM_SCHEDULER";

    private Auditor() {
    }
}
