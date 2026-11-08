package com.shineunsigan.snsg.schedule.application.dto.result;

/** schedule이 지역에 대해 아는 값. 서비스 중(활성)인 지역인지만 본다. */
public record ScheduleRegionResult(String code, boolean active) {
}
