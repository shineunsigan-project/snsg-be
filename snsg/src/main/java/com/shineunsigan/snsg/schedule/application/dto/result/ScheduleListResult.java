package com.shineunsigan.snsg.schedule.application.dto.result;

import java.util.List;

/**
 * 화면에 보낼 일정 목록 한 페이지.
 *
 * @param nextCursor 다음 페이지 커서. 마지막 페이지면 null
 */
public record ScheduleListResult(List<ScheduleListItemResult> items, String nextCursor) {
}
