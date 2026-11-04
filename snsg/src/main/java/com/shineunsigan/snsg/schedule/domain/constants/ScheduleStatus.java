package com.shineunsigan.snsg.schedule.domain.constants;

/**
 * 일정 자체의 상태. 한 번 CANCELED·COMPLETED가 되면 되돌리지 않는다.
 */
public enum ScheduleStatus {
    SCHEDULED,
    CANCELED,
    COMPLETED
}
