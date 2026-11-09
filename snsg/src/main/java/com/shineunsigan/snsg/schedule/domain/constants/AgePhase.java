package com.shineunsigan.snsg.schedule.domain.constants;

/**
 * 연령대의 초반·중반·후반. user 도메인의 값을 어댑터가 옮겨 담는다.
 * 선언 순서가 비교 순서다(초반 &lt; 중반 &lt; 후반).
 */
public enum AgePhase {

    EARLY,
    MID,
    LATE
}
