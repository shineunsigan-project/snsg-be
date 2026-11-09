package com.shineunsigan.snsg.schedule.domain.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

import com.shineunsigan.snsg.schedule.domain.constants.Gender;

/**
 * 일정 상세의 참여자 요약. 확정 참여자(개설자 포함)의 수, 성별 수, 가장 낮은·높은 연령대다.
 *
 * @param count       확정 참여자 수(개설자 포함)
 * @param maleCount   성별이 남자인 수. 탈퇴자도 센다. 성별을 알 수 없는 참여자는 어느 쪽에도 세지 않는다
 * @param femaleCount 성별이 여자인 수. 세는 기준은 maleCount와 같다
 * @param minAgeGroup 연령대가 있는 참여자 중 가장 낮은 연령대. 연령대가 있는 참여자가 없으면 {@code null}
 * @param maxAgeGroup 연령대가 있는 참여자 중 가장 높은 연령대. 연령대가 있는 참여자가 없으면 {@code null}
 */
public record ParticipantSummary(int count, int maleCount, int femaleCount, ParticipantAgeGroup minAgeGroup,
        ParticipantAgeGroup maxAgeGroup) {

    /** @param participants 확정 참여자마다 하나 */
    public static ParticipantSummary of(Collection<ParticipantProfile> participants) {
        return new ParticipantSummary(participants.size(), countGender(participants, Gender.MALE),
                countGender(participants, Gender.FEMALE),
                ageGroups(participants).min(Comparator.naturalOrder()).orElse(null),
                ageGroups(participants).max(Comparator.naturalOrder()).orElse(null));
    }

    private static int countGender(Collection<ParticipantProfile> participants, Gender gender) {
        return (int) participants.stream().filter(participant -> participant.gender() == gender).count();
    }

    /** 연령대가 없는 참여자(탈퇴자·사용자 정보 없음)는 뺀다. */
    private static Stream<ParticipantAgeGroup> ageGroups(Collection<ParticipantProfile> participants) {
        return participants.stream().map(ParticipantProfile::ageGroup).filter(Objects::nonNull);
    }
}
