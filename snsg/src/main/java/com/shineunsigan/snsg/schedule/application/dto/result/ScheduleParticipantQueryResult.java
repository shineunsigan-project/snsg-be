package com.shineunsigan.snsg.schedule.application.dto.result;

import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.constants.ParticipantRole;
import com.shineunsigan.snsg.schedule.domain.model.ScheduleParticipant;

/**
 * schedule이 가진 데이터만으로 만든 명단 항목. 프로필은 facade가 채운다.
 *
 * @param isHost 개설자 행이면 {@code true}
 */
public record ScheduleParticipantQueryResult(UUID userId, boolean isHost) {

    public static ScheduleParticipantQueryResult from(ScheduleParticipant participant) {
        return new ScheduleParticipantQueryResult(participant.getUserId(),
                participant.getRole() == ParticipantRole.HOST);
    }
}
