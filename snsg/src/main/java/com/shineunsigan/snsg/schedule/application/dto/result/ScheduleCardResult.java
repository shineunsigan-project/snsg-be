package com.shineunsigan.snsg.schedule.application.dto.result;

import java.time.LocalDateTime;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;

/**
 * 일정 목록의 카드 한 장. 지역은 코드만 담는다. 이름은 facade가 place 도메인에서 채운다.
 *
 * @param categoryName 활동 이름. 비활성 활동이어도 이름이다. 활동이 없거나 삭제됐으면 빈 문자열
 */
public record ScheduleCardResult(UUID id, UUID categoryId, String categoryName, String title, String regionCode,
        LocalDateTime startAt, int confirmedCount, int capacity, int remainingSeats, DisplayStatus displayStatus,
        LocalDateTime createdAt) {
}
