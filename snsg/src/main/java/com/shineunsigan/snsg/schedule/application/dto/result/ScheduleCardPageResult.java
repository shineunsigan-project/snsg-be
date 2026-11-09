package com.shineunsigan.snsg.schedule.application.dto.result;

import java.util.List;

/**
 * 일정 카드 한 페이지.
 *
 * @param nextCursor 다음 페이지 커서. 마지막 페이지면 null
 */
public record ScheduleCardPageResult(List<ScheduleCardResult> items, String nextCursor) {
}
