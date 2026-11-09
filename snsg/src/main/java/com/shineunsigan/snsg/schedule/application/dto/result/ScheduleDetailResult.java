package com.shineunsigan.snsg.schedule.application.dto.result;

/**
 * 일정 상세.
 *
 * @param regionName 지역 이름(시군구). 지역이 없으면 빈 문자열
 */
public record ScheduleDetailResult(ScheduleDetailQueryResult schedule, ScheduleHostResult host,
        ParticipantSummaryResult participantSummary, String regionName) {
}
