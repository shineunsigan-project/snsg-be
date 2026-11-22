package com.shineunsigan.snsg.schedule.application.dto.command;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.constants.RecruitingScheduleSort;

/**
 * 모집 중 목록 조회 조건. sort를 뺀 null은 생략이다.
 *
 * @param sort   정렬. null이 아니다(생략하면 presentation이 START_AT으로 채운다)
 * @param cursor 앞 페이지의 nextCursor. null이나 빈 문자열이면 처음부터
 * @param size   한 페이지 개수. null이면 기본 개수
 */
public record ListRecruitingSchedulesCommand(List<UUID> categoryIds, List<String> regionCodes, LocalDate startDate,
        LocalDate endDate, RecruitingScheduleSort sort, String cursor, Integer size) {
}
