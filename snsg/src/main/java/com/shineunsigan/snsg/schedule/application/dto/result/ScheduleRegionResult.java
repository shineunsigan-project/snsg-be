package com.shineunsigan.snsg.schedule.application.dto.result;

/**
 * schedule이 지역에 대해 아는 값. 서비스 중(활성)인 지역인지와 화면에 보일 이름이다.
 *
 * @param name 화면에 보일 지역 이름(시군구, 예: 마포구)
 */
public record ScheduleRegionResult(String code, String name, boolean active) {
}
