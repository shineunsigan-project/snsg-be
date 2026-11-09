package com.shineunsigan.snsg.schedule.application.dto.result;

import com.shineunsigan.snsg.schedule.domain.model.ParticipantSummary;

/**
 * 일정 상세의 참여자 요약. 계산 규칙은 {@link ParticipantSummary}에 있다.
 *
 * @param minAgeGroup 연령대가 있는 참여자가 없으면(전원 탈퇴) {@code null}
 * @param maxAgeGroup 연령대가 있는 참여자가 없으면(전원 탈퇴) {@code null}
 */
public record ParticipantSummaryResult(int count, int maleCount, int femaleCount, ScheduleAgeGroupResult minAgeGroup,
        ScheduleAgeGroupResult maxAgeGroup) {

    public static ParticipantSummaryResult from(ParticipantSummary summary) {
        return new ParticipantSummaryResult(summary.count(), summary.maleCount(), summary.femaleCount(),
                ScheduleAgeGroupResult.from(summary.minAgeGroup()), ScheduleAgeGroupResult.from(summary.maxAgeGroup()));
    }
}
