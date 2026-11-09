package com.shineunsigan.snsg.schedule.application.dto.result;

import com.shineunsigan.snsg.schedule.domain.constants.AgePhase;
import com.shineunsigan.snsg.schedule.domain.model.ParticipantAgeGroup;

/**
 * 연령대(예: 20대 후반).
 *
 * @param decade 십의 자리 × 10 (20대면 20)
 */
public record ScheduleAgeGroupResult(int decade, AgePhase phase) {

    /** 연령대가 없으면 {@code null}이다. */
    public static ScheduleAgeGroupResult from(ParticipantAgeGroup ageGroup) {
        return ageGroup == null ? null : new ScheduleAgeGroupResult(ageGroup.decade(), ageGroup.phase());
    }

    public ParticipantAgeGroup toAgeGroup() {
        return new ParticipantAgeGroup(decade, phase);
    }
}
