package com.shineunsigan.snsg.schedule.domain.constants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 예상 소요 시간. 예상 종료 시각은 저장하지 않고 시작 시각에 이 분을 더해 구한다.
 */
@Getter
@RequiredArgsConstructor
public enum DurationType {

    /** 1시간 내외. */
    SHORT(60),

    /** 2시간 내외. */
    NORMAL(120),

    /** 3시간 이상. */
    LONG(180);

    private final int minutes;
}
