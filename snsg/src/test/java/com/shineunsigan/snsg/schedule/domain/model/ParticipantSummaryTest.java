package com.shineunsigan.snsg.schedule.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shineunsigan.snsg.schedule.domain.constants.AgePhase;
import com.shineunsigan.snsg.schedule.domain.constants.Gender;

/**
 * 참여자 요약의 수 세기(탈퇴자·성별을 모르는 참여자)와 최저·최고 연령대 고르기를 검증한다.
 * 탈퇴자는 성별이 있고 연령대가 없다. 사용자 정보를 찾을 수 없는 참여자는 둘 다 없다.
 */
class ParticipantSummaryTest {

    @Test
    @DisplayName("count는 확정 참여자 수이고, 남자·여자 수는 성별대로 센다")
    void counts() {
        ParticipantSummary summary = ParticipantSummary.of(List.of(
                member(Gender.MALE, 20, AgePhase.EARLY),
                member(Gender.FEMALE, 20, AgePhase.MID),
                member(Gender.FEMALE, 30, AgePhase.LATE)));

        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.maleCount()).isEqualTo(1);
        assertThat(summary.femaleCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("탈퇴자는 성별대로 남자·여자 수에 세고, 성별이 없는 참여자는 어느 쪽에도 세지 않는다. count에는 모두 든다")
    void withdrawnAndMissing() {
        ParticipantSummary summary = ParticipantSummary.of(List.of(
                member(Gender.MALE, 20, AgePhase.EARLY),
                withdrawn(Gender.FEMALE),
                missing()));

        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.maleCount()).isEqualTo(1);
        assertThat(summary.femaleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("연령대는 십의 자리를 먼저 비교한다. 20대 후반이 30대 초반보다 낮다")
    void decadeFirst() {
        ParticipantSummary summary = ParticipantSummary.of(List.of(
                member(Gender.MALE, 30, AgePhase.EARLY),
                member(Gender.FEMALE, 20, AgePhase.LATE),
                member(Gender.FEMALE, 40, AgePhase.EARLY)));

        assertThat(summary.minAgeGroup()).isEqualTo(new ParticipantAgeGroup(20, AgePhase.LATE));
        assertThat(summary.maxAgeGroup()).isEqualTo(new ParticipantAgeGroup(40, AgePhase.EARLY));
    }

    @Test
    @DisplayName("십의 자리가 같으면 초반 < 중반 < 후반이다")
    void phaseWithinDecade() {
        ParticipantSummary summary = ParticipantSummary.of(List.of(
                member(Gender.MALE, 20, AgePhase.MID),
                member(Gender.FEMALE, 20, AgePhase.LATE),
                member(Gender.FEMALE, 20, AgePhase.EARLY)));

        assertThat(summary.minAgeGroup()).isEqualTo(new ParticipantAgeGroup(20, AgePhase.EARLY));
        assertThat(summary.maxAgeGroup()).isEqualTo(new ParticipantAgeGroup(20, AgePhase.LATE));
    }

    @Test
    @DisplayName("연령대가 없는 참여자(탈퇴자·사용자 정보 없음)는 빼고 고른다")
    void skipsWithoutAgeGroup() {
        ParticipantSummary summary = ParticipantSummary.of(List.of(
                withdrawn(Gender.MALE),
                member(Gender.FEMALE, 30, AgePhase.MID),
                missing()));

        assertThat(summary.minAgeGroup()).isEqualTo(new ParticipantAgeGroup(30, AgePhase.MID));
        assertThat(summary.maxAgeGroup()).isEqualTo(new ParticipantAgeGroup(30, AgePhase.MID));
    }

    @Test
    @DisplayName("연령대가 있는 참여자가 하나도 없으면(전원 탈퇴) 최저·최고 연령대는 null")
    void allWithdrawn() {
        ParticipantSummary summary = ParticipantSummary.of(List.of(withdrawn(Gender.MALE), withdrawn(Gender.FEMALE)));

        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.minAgeGroup()).isNull();
        assertThat(summary.maxAgeGroup()).isNull();
    }

    private static ParticipantProfile member(Gender gender, int decade, AgePhase phase) {
        return new ParticipantProfile(gender, new ParticipantAgeGroup(decade, phase));
    }

    private static ParticipantProfile withdrawn(Gender gender) {
        return new ParticipantProfile(gender, null);
    }

    private static ParticipantProfile missing() {
        return new ParticipantProfile(null, null);
    }
}
