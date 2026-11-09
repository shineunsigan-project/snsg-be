package com.shineunsigan.snsg.schedule.application.dto.result;

/**
 * 화면에 보낼 일정 카드. 카드에 지역 이름을 붙인 것이다.
 *
 * @param regionName 지역 이름(예: 마포구). 지역 목록에 없는 코드면 빈 문자열
 */
public record ScheduleListItemResult(ScheduleCardResult card, String regionName) {
}
