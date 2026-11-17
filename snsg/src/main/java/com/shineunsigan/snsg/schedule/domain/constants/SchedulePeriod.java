package com.shineunsigan.snsg.schedule.domain.constants;

/**
 * 참여한·개설한 일정 목록의 기간 구분. 두 값이 서로의 여집합이라 합치면 빠짐도 겹침도 없다.
 * 완료 기준 시각은 지금 − 완료 기준 시간(schedule.completion-after)이다.
 */
public enum SchedulePeriod {

    /** 완료 전 일정. 완료(COMPLETED)가 아니고 시작 시각이 완료 기준 시각보다 뒤다. 시작 빠른 순. */
    UPCOMING,

    /** 지난 일정. 완료(COMPLETED)이거나 시작 시각이 완료 기준 시각 이하다. 시작 늦은 순. */
    PAST
}
