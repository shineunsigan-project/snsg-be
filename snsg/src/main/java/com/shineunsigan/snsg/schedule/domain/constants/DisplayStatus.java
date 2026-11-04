package com.shineunsigan.snsg.schedule.domain.constants;

/**
 * 화면에 보여줄 상태. 일정 상태·모집 상태·시각을 하나로 합친 값이라 저장하지 않는다.
 * FE가 축 조합을 해석하지 않도록 서버가 판정한다.
 */
public enum DisplayStatus {
    CANCELED,
    COMPLETED,
    RECRUITMENT_CLOSED,
    RECRUITING
}
