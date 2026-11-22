package com.shineunsigan.snsg.schedule.application.dto.result;

import java.util.List;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.constants.DurationType;

/**
 * schedule이 가진 데이터만으로 만든 다가오는 내 일정. 지역 이름과 참여자 사진은 facade가 채운다.
 *
 * @param placeName        개설 시점의 장소 이름
 * @param confirmedUserIds 확정 참여자(개설자 포함)의 사용자 id. 명단 순서(개설자 먼저 → 참여 일시 → 행 id)다
 */
public record NextScheduleQueryResult(ScheduleCardResult card, String placeName, DurationType durationType,
        List<UUID> confirmedUserIds) {
}
