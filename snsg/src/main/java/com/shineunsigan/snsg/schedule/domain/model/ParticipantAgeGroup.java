package com.shineunsigan.snsg.schedule.domain.model;

import java.util.Comparator;

import com.shineunsigan.snsg.schedule.domain.constants.AgePhase;

/**
 * 참여자 한 명의 연령대. 십의 자리를 먼저 비교하고, 같으면 초반 &lt; 중반 &lt; 후반이다(20대 후반 &lt; 30대 초반).
 *
 * @param decade 십의 자리 × 10 (20대면 20)
 */
public record ParticipantAgeGroup(int decade, AgePhase phase) implements Comparable<ParticipantAgeGroup> {

    private static final Comparator<ParticipantAgeGroup> ORDER = Comparator.comparingInt(ParticipantAgeGroup::decade)
            .thenComparing(ParticipantAgeGroup::phase);

    @Override
    public int compareTo(ParticipantAgeGroup other) {
        return ORDER.compare(this, other);
    }
}
