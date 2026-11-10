package com.shineunsigan.snsg.schedule.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.constants.HostType;
import com.shineunsigan.snsg.schedule.domain.constants.ParticipantRole;
import com.shineunsigan.snsg.schedule.domain.constants.ParticipationStatus;
import com.shineunsigan.snsg.schedule.domain.constants.RecruitmentStatus;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleAction;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleStatus;
import com.shineunsigan.snsg.schedule.domain.constants.ViewerRole;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

/**
 * 개설의 형식 규칙(제목·설명·시작 일시·인원), 화면 표시 상태 판정, 참여·참여 취소 규칙,
 * 상세를 보는 사람의 역할과 누를 수 있는 동작, 참여자 명단을 볼 수 있는 사람과 명단 순서를 검증한다.
 * 설정값은 운영 설정과 같은 값(4~24명, 1시간, 30일, 완료 12시간)을 넣는다.
 */
class ScheduleTest {

    private static final ScheduleCreationRule RULE = new ScheduleCreationRule(4, 24, Duration.ofHours(1), 30);
    private static final Duration COMPLETION_AFTER = Duration.ofHours(12);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0, 0);
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 11, 11, 19, 30);
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID PLACE_ID = UUID.randomUUID();
    private static final String TITLE = "보드게임 같이 해요";
    private static final String DESCRIPTION = "처음 오시는 분도 환영해요.";
    private static final int CAPACITY = 6;

    @Nested
    @DisplayName("개설 직후")
    class Created {

        @Test
        @DisplayName("예정·모집 중이고 개설 주체는 사용자, 확정 인원은 1이다")
        void statesAfterCreate() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);

            assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.SCHEDULED);
            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.RECRUITING);
            assertThat(schedule.getHostType()).isEqualTo(HostType.USER);
            assertThat(schedule.getConfirmedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("참여 행은 개설자의 확정 HOST 행 하나뿐이다")
        void onlyHostParticipant() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);

            assertThat(schedule.getParticipants()).singleElement().satisfies(participant -> {
                assertThat(participant.getUserId()).isEqualTo(HOST_ID);
                assertThat(participant.getRole()).isEqualTo(ParticipantRole.HOST);
                assertThat(participant.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
                assertThat(participant.getSchedule()).isSameAs(schedule);
            });
        }

        @Test
        @DisplayName("장소 id와 장소 사본을 받은 그대로 가진다")
        void keepsPlace() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);

            assertThat(schedule.getPlaceId()).isEqualTo(PLACE_ID);
            assertThat(schedule.getPlace().getPlaceName()).isEqualTo("보드게임카페");
            assertThat(schedule.getPlace().getRegionCode()).isEqualTo("11440");
        }
    }

    @Nested
    @DisplayName("활동명")
    class Title {

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("공백을 뺀 값이 0자면 INVALID_TITLE")
        void empty(String title) {
            assertInvalid(() -> create(title, DESCRIPTION, START_AT, CAPACITY, NOW), ScheduleErrorCode.INVALID_TITLE);
        }

        @Test
        @DisplayName("1자는 허용한다")
        void oneCharacter() {
            assertThat(create("가", DESCRIPTION, START_AT, CAPACITY, NOW).getTitle()).isEqualTo("가");
        }

        @Test
        @DisplayName("20자는 허용한다")
        void twentyCharacters() {
            String title = "가".repeat(20);

            assertThat(create(title, DESCRIPTION, START_AT, CAPACITY, NOW).getTitle()).isEqualTo(title);
        }

        @Test
        @DisplayName("21자면 INVALID_TITLE")
        void twentyOneCharacters() {
            assertInvalid(() -> create("가".repeat(21), DESCRIPTION, START_AT, CAPACITY, NOW),
                    ScheduleErrorCode.INVALID_TITLE);
        }

        @Test
        @DisplayName("앞뒤 공백을 포함해 22자여도 뺀 값이 20자면 허용하고, 뺀 값을 저장한다")
        void stripsBeforeCounting() {
            String title = "가".repeat(20);

            assertThat(create(" " + title + " ", DESCRIPTION, START_AT, CAPACITY, NOW).getTitle()).isEqualTo(title);
        }
    }

    @Nested
    @DisplayName("설명")
    class Description {

        @Test
        @DisplayName("9자면 INVALID_DESCRIPTION")
        void nineCharacters() {
            assertInvalid(() -> create(TITLE, "가".repeat(9), START_AT, CAPACITY, NOW),
                    ScheduleErrorCode.INVALID_DESCRIPTION);
        }

        @Test
        @DisplayName("10자는 허용한다")
        void tenCharacters() {
            assertThat(create(TITLE, "가".repeat(10), START_AT, CAPACITY, NOW).getDescription())
                    .isEqualTo("가".repeat(10));
        }

        @Test
        @DisplayName("500자는 허용한다")
        void fiveHundredCharacters() {
            assertThat(create(TITLE, "가".repeat(500), START_AT, CAPACITY, NOW).getDescription()).hasSize(500);
        }

        @Test
        @DisplayName("501자면 INVALID_DESCRIPTION")
        void fiveHundredOneCharacters() {
            assertInvalid(() -> create(TITLE, "가".repeat(501), START_AT, CAPACITY, NOW),
                    ScheduleErrorCode.INVALID_DESCRIPTION);
        }

        @Test
        @DisplayName("앞뒤 공백을 빼고 세고, 뺀 값을 저장한다")
        void stripsBeforeCounting() {
            assertThat(create(TITLE, "  " + "가".repeat(10) + "\n", START_AT, CAPACITY, NOW).getDescription())
                    .isEqualTo("가".repeat(10));
            assertInvalid(() -> create(TITLE, "  " + "가".repeat(9) + "  ", START_AT, CAPACITY, NOW),
                    ScheduleErrorCode.INVALID_DESCRIPTION);
        }
    }

    @Nested
    @DisplayName("시작 일시")
    class StartAt {

        @Test
        @DisplayName("분이 00·30이 아니면 INVALID_START_AT")
        void minuteNotHalfHour() {
            assertInvalid(() -> create(TITLE, DESCRIPTION, START_AT.withMinute(15), CAPACITY, NOW),
                    ScheduleErrorCode.INVALID_START_AT);
        }

        @Test
        @DisplayName("초가 0이 아니면 INVALID_START_AT")
        void secondNotZero() {
            assertInvalid(() -> create(TITLE, DESCRIPTION, START_AT.withSecond(1), CAPACITY, NOW),
                    ScheduleErrorCode.INVALID_START_AT);
        }

        @Test
        @DisplayName("지금이 10:00:00이면 11:00은 허용한다(정확히 1시간 뒤)")
        void exactlyOneHourLater() {
            LocalDateTime startAt = NOW.plusHours(1);

            assertThat(create(TITLE, DESCRIPTION, startAt, CAPACITY, NOW).getStartAt()).isEqualTo(startAt);
        }

        @Test
        @DisplayName("지금이 10:00:00이면 10:30은 INVALID_START_AT")
        void lessThanOneHourLater() {
            assertInvalid(() -> create(TITLE, DESCRIPTION, NOW.withMinute(30), CAPACITY, NOW),
                    ScheduleErrorCode.INVALID_START_AT);
        }

        @Test
        @DisplayName("지금이 10:00:01이면 11:00은 INVALID_START_AT")
        void oneSecondShort() {
            assertInvalid(() -> create(TITLE, DESCRIPTION, NOW.plusHours(1), CAPACITY, NOW.withSecond(1)),
                    ScheduleErrorCode.INVALID_START_AT);
        }

        @Test
        @DisplayName("오늘 + 30일째 날 23:30은 허용한다")
        void lastAllowedDay() {
            LocalDateTime startAt = NOW.toLocalDate().plusDays(30).atTime(23, 30);

            assertThat(create(TITLE, DESCRIPTION, startAt, CAPACITY, NOW).getStartAt()).isEqualTo(startAt);
        }

        @Test
        @DisplayName("오늘 + 31일째 날 00:00은 INVALID_START_AT")
        void dayAfterLastAllowedDay() {
            LocalDateTime startAt = NOW.toLocalDate().plusDays(31).atStartOfDay();

            assertInvalid(() -> create(TITLE, DESCRIPTION, startAt, CAPACITY, NOW), ScheduleErrorCode.INVALID_START_AT);
        }
    }

    @Nested
    @DisplayName("인원")
    class Capacity {

        @ParameterizedTest
        @ValueSource(ints = {3, 25})
        @DisplayName("4명 미만이나 24명 초과면 INVALID_CAPACITY")
        void outOfRange(int capacity) {
            assertInvalid(() -> create(TITLE, DESCRIPTION, START_AT, capacity, NOW), ScheduleErrorCode.INVALID_CAPACITY);
        }

        @ParameterizedTest
        @ValueSource(ints = {4, 24})
        @DisplayName("4명과 24명은 허용한다")
        void boundaries(int capacity) {
            assertThat(create(TITLE, DESCRIPTION, START_AT, capacity, NOW).getCapacity()).isEqualTo(capacity);
        }
    }

    @Nested
    @DisplayName("표시 상태")
    class Display {

        private final Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);

        @Test
        @DisplayName("시작 직전이면 RECRUITING")
        void beforeStart() {
            assertThat(schedule.displayStatus(START_AT.minusMinutes(1), COMPLETION_AFTER))
                    .isEqualTo(DisplayStatus.RECRUITING);
        }

        @Test
        @DisplayName("시작 시각이 되면 스케줄러가 마감하기 전이어도 RECRUITMENT_CLOSED")
        void atStartBeforeScheduler() {
            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.RECRUITING);
            assertThat(schedule.displayStatus(START_AT, COMPLETION_AFTER))
                    .isEqualTo(DisplayStatus.RECRUITMENT_CLOSED);
        }

        @Test
        @DisplayName("시작 + 12시간 1분 전이면 아직 RECRUITMENT_CLOSED")
        void justBeforeCompletion() {
            assertThat(schedule.displayStatus(START_AT.plusHours(12).minusMinutes(1), COMPLETION_AFTER))
                    .isEqualTo(DisplayStatus.RECRUITMENT_CLOSED);
        }

        @Test
        @DisplayName("시작 + 12시간이 되면 스케줄러가 완료하기 전이어도 COMPLETED")
        void atCompletion() {
            assertThat(schedule.displayStatus(START_AT.plusHours(12), COMPLETION_AFTER))
                    .isEqualTo(DisplayStatus.COMPLETED);
        }

        @Test
        @DisplayName("모집 마감 상태면 시작 전이어도 RECRUITMENT_CLOSED")
        void closedBeforeStart() {
            ReflectionTestUtils.setField(schedule, "recruitmentStatus", RecruitmentStatus.CLOSED);

            assertThat(schedule.displayStatus(START_AT.minusHours(1), COMPLETION_AFTER))
                    .isEqualTo(DisplayStatus.RECRUITMENT_CLOSED);
        }

        @Test
        @DisplayName("완료 상태면 시작 전이어도 COMPLETED")
        void completedStatus() {
            ReflectionTestUtils.setField(schedule, "status", ScheduleStatus.COMPLETED);

            assertThat(schedule.displayStatus(START_AT.minusHours(1), COMPLETION_AFTER))
                    .isEqualTo(DisplayStatus.COMPLETED);
        }

        @Test
        @DisplayName("취소 상태면 시각과 무관하게 CANCELED")
        void canceledRegardlessOfTime() {
            ReflectionTestUtils.setField(schedule, "status", ScheduleStatus.CANCELED);
            ReflectionTestUtils.setField(schedule, "recruitmentStatus", RecruitmentStatus.CLOSED);

            assertThat(schedule.displayStatus(START_AT.minusHours(1), COMPLETION_AFTER))
                    .isEqualTo(DisplayStatus.CANCELED);
            assertThat(schedule.displayStatus(START_AT, COMPLETION_AFTER)).isEqualTo(DisplayStatus.CANCELED);
            assertThat(schedule.displayStatus(START_AT.plusHours(12), COMPLETION_AFTER))
                    .isEqualTo(DisplayStatus.CANCELED);
        }
    }

    @Nested
    @DisplayName("참여")
    class Join {

        private final UUID memberId = UUID.randomUUID();
        private final LocalDateTime joinAt = START_AT.minusHours(1);

        @Test
        @DisplayName("참여하면 MEMBER·CONFIRMED 행이 더해지고 확정 인원이 1 늘며, 정원 전이면 RECRUITING 그대로다")
        void joins() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);

            schedule.join(memberId, joinAt, COMPLETION_AFTER);

            assertThat(rowsOf(schedule, memberId)).singleElement().satisfies(participant -> {
                assertThat(participant.getRole()).isEqualTo(ParticipantRole.MEMBER);
                assertThat(participant.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
                assertThat(participant.getSchedule()).isSameAs(schedule);
            });
            assertThat(schedule.getConfirmedCount()).isEqualTo(2);
            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.RECRUITING);
        }

        @Test
        @DisplayName("참여로 확정 인원이 정원에 닿으면 CLOSED가 된다")
        void closesWhenFull() {
            Schedule schedule = fullAfterOneMoreJoin();

            schedule.join(memberId, joinAt, COMPLETION_AFTER);

            assertThat(schedule.getConfirmedCount()).isEqualTo(4);
            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
        }

        @Test
        @DisplayName("개설자가 참여하면 HOST_CANNOT_JOIN")
        void hostCannotJoin() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);

            assertJoinRejected(schedule, HOST_ID, joinAt, ScheduleErrorCode.HOST_CANNOT_JOIN);
        }

        @Test
        @DisplayName("모집이 마감된 일정이어도 개설자는 HOST_CANNOT_JOIN")
        void hostCannotJoinClosedSchedule() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);
            ReflectionTestUtils.setField(schedule, "recruitmentStatus", RecruitmentStatus.CLOSED);

            assertJoinRejected(schedule, HOST_ID, joinAt, ScheduleErrorCode.HOST_CANNOT_JOIN);
        }

        @Test
        @DisplayName("이미 확정 참여한 사용자가 다시 참여하면 ALREADY_JOINED")
        void alreadyJoined() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);
            schedule.join(memberId, joinAt, COMPLETION_AFTER);

            assertJoinRejected(schedule, memberId, joinAt, ScheduleErrorCode.ALREADY_JOINED);
        }

        @Test
        @DisplayName("그 참여로 정원이 차서 CLOSED가 된 뒤에 다시 참여해도 ALREADY_JOINED")
        void alreadyJoinedAfterClosedByOwnJoin() {
            Schedule schedule = fullAfterOneMoreJoin();
            schedule.join(memberId, joinAt, COMPLETION_AFTER);

            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
            assertJoinRejected(schedule, memberId, joinAt, ScheduleErrorCode.ALREADY_JOINED);
        }

        @Test
        @DisplayName("모집 상태가 CLOSED면 RECRUIT_CLOSED")
        void closedSchedule() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);
            ReflectionTestUtils.setField(schedule, "recruitmentStatus", RecruitmentStatus.CLOSED);

            assertJoinRejected(schedule, memberId, joinAt, ScheduleErrorCode.RECRUIT_CLOSED);
        }

        @Test
        @DisplayName("저장된 모집 상태가 RECRUITING이어도 시작 시각이 되면 RECRUIT_CLOSED")
        void atStartBeforeScheduler() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);

            assertJoinRejected(schedule, memberId, START_AT, ScheduleErrorCode.RECRUIT_CLOSED);
        }

        @Test
        @DisplayName("저장된 모집 상태가 RECRUITING이어도 시작 시각이 지났으면 RECRUIT_CLOSED")
        void afterStartBeforeScheduler() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);

            assertJoinRejected(schedule, memberId, START_AT.plusMinutes(1), ScheduleErrorCode.RECRUIT_CLOSED);
        }

        @ParameterizedTest
        @EnumSource(value = ScheduleStatus.class, names = {"CANCELED", "COMPLETED"})
        @DisplayName("일정이 취소·완료 상태면 RECRUIT_CLOSED")
        void canceledOrCompleted(ScheduleStatus status) {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);
            ReflectionTestUtils.setField(schedule, "status", status);

            assertJoinRejected(schedule, memberId, joinAt, ScheduleErrorCode.RECRUIT_CLOSED);
        }

        @Test
        @DisplayName("참여 → 참여 취소 → 재참여하면 행이 둘(이전 CANCELED, 새 CONFIRMED)이고 확정 인원은 참여 한 번과 같다")
        void rejoinAddsNewRow() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);

            schedule.join(memberId, joinAt, COMPLETION_AFTER);
            schedule.leave(memberId, joinAt, COMPLETION_AFTER);
            schedule.join(memberId, joinAt, COMPLETION_AFTER);

            assertThat(rowsOf(schedule, memberId))
                    .extracting(ScheduleParticipant::getStatus)
                    .containsExactly(ParticipationStatus.CANCELED, ParticipationStatus.CONFIRMED);
            assertThat(schedule.getConfirmedCount()).isEqualTo(2);
        }

        /** 정원 4에 확정 3(개설자 + 멤버 2). 한 명 더 들어오면 정원이다. */
        private Schedule fullAfterOneMoreJoin() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, 4, NOW);
            schedule.join(UUID.randomUUID(), joinAt, COMPLETION_AFTER);
            schedule.join(UUID.randomUUID(), joinAt, COMPLETION_AFTER);
            return schedule;
        }

        private void assertJoinRejected(Schedule schedule, UUID userId, LocalDateTime now,
                ScheduleErrorCode errorCode) {
            Snapshot before = Snapshot.of(schedule);

            assertInvalid(() -> schedule.join(userId, now, COMPLETION_AFTER), errorCode);
            assertThat(Snapshot.of(schedule)).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("참여 취소")
    class Leave {

        private final UUID memberId = UUID.randomUUID();
        private final LocalDateTime joinAt = START_AT.minusHours(1);

        @Test
        @DisplayName("시작 전에 취소하면 행이 CANCELED, 확정 인원 -1이고 RECRUITING 그대로다")
        void beforeStart() {
            Schedule schedule = joined(CAPACITY);

            schedule.leave(memberId, START_AT.minusMinutes(1), COMPLETION_AFTER);

            assertThat(rowsOf(schedule, memberId)).singleElement()
                    .extracting(ScheduleParticipant::getStatus)
                    .isEqualTo(ParticipationStatus.CANCELED);
            assertThat(schedule.getConfirmedCount()).isEqualTo(1);
            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.RECRUITING);
        }

        @Test
        @DisplayName("시작 전에 취소하면 CLOSED였던 모집이 RECRUITING으로 돌아간다")
        void beforeStartReopens() {
            Schedule schedule = joinedFull();

            schedule.leave(memberId, START_AT.minusMinutes(1), COMPLETION_AFTER);

            assertThat(rowsOf(schedule, memberId)).singleElement()
                    .extracting(ScheduleParticipant::getStatus)
                    .isEqualTo(ParticipationStatus.CANCELED);
            assertThat(schedule.getConfirmedCount()).isEqualTo(3);
            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.RECRUITING);
        }

        @Test
        @DisplayName("시작 시각에 취소하면 행이 CANCELED, 확정 인원 -1이고 CLOSED는 그대로다")
        void atStartKeepsClosed() {
            Schedule schedule = joinedFull();

            schedule.leave(memberId, START_AT, COMPLETION_AFTER);

            assertThat(rowsOf(schedule, memberId)).singleElement()
                    .extracting(ScheduleParticipant::getStatus)
                    .isEqualTo(ParticipationStatus.CANCELED);
            assertThat(schedule.getConfirmedCount()).isEqualTo(3);
            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
        }

        @Test
        @DisplayName("시작 + 12시간 1분 전에 취소해도 되고 CLOSED는 그대로다")
        void justBeforeCompletion() {
            Schedule schedule = joinedFull();

            schedule.leave(memberId, START_AT.plusHours(12).minusMinutes(1), COMPLETION_AFTER);

            assertThat(schedule.getConfirmedCount()).isEqualTo(3);
            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
        }

        @Test
        @DisplayName("시작 후 취소는 저장된 모집 상태가 RECRUITING(스케줄러 전)이어도 그대로 둔다")
        void afterStartKeepsRecruiting() {
            Schedule schedule = joined(CAPACITY);

            schedule.leave(memberId, START_AT.plusHours(1), COMPLETION_AFTER);

            assertThat(schedule.getConfirmedCount()).isEqualTo(1);
            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.RECRUITING);
        }

        @Test
        @DisplayName("시작 + 12시간이 되면 스케줄러 전이어도 PARTICIPATION_NOT_CANCELABLE")
        void atCompletion() {
            Schedule schedule = joined(CAPACITY);

            assertLeaveRejected(schedule, memberId, START_AT.plusHours(12),
                    ScheduleErrorCode.PARTICIPATION_NOT_CANCELABLE);
        }

        @Test
        @DisplayName("시작 + 12시간이 지났으면 PARTICIPATION_NOT_CANCELABLE")
        void afterCompletion() {
            Schedule schedule = joined(CAPACITY);

            assertLeaveRejected(schedule, memberId, START_AT.plusHours(13),
                    ScheduleErrorCode.PARTICIPATION_NOT_CANCELABLE);
        }

        @ParameterizedTest
        @EnumSource(value = ScheduleStatus.class, names = {"CANCELED", "COMPLETED"})
        @DisplayName("일정이 취소·완료 상태면 시작 전이어도 PARTICIPATION_NOT_CANCELABLE")
        void canceledOrCompleted(ScheduleStatus status) {
            Schedule schedule = joined(CAPACITY);
            ReflectionTestUtils.setField(schedule, "status", status);

            assertLeaveRejected(schedule, memberId, joinAt, ScheduleErrorCode.PARTICIPATION_NOT_CANCELABLE);
        }

        @Test
        @DisplayName("개설자가 취소하면 HOST_CANNOT_LEAVE")
        void hostCannotLeave() {
            Schedule schedule = joined(CAPACITY);

            assertLeaveRejected(schedule, HOST_ID, joinAt, ScheduleErrorCode.HOST_CANNOT_LEAVE);
        }

        @Test
        @DisplayName("참여한 적 없는 사용자는 NOT_PARTICIPANT")
        void neverJoined() {
            Schedule schedule = joined(CAPACITY);

            assertLeaveRejected(schedule, UUID.randomUUID(), joinAt, ScheduleErrorCode.NOT_PARTICIPANT);
        }

        @Test
        @DisplayName("이미 취소한 사용자는 NOT_PARTICIPANT")
        void alreadyLeft() {
            Schedule schedule = joined(CAPACITY);
            schedule.leave(memberId, joinAt, COMPLETION_AFTER);

            assertLeaveRejected(schedule, memberId, joinAt, ScheduleErrorCode.NOT_PARTICIPANT);
        }

        @Test
        @DisplayName("이미 취소한 사용자는 일정이 완료됐어도 NOT_PARTICIPANT")
        void alreadyLeftCompletedSchedule() {
            Schedule schedule = joined(CAPACITY);
            schedule.leave(memberId, joinAt, COMPLETION_AFTER);
            ReflectionTestUtils.setField(schedule, "status", ScheduleStatus.COMPLETED);

            assertLeaveRejected(schedule, memberId, START_AT.plusHours(13), ScheduleErrorCode.NOT_PARTICIPANT);
        }

        /** 개설자와 memberId가 확정 참여한 일정. */
        private Schedule joined(int capacity) {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, capacity, NOW);
            schedule.join(memberId, joinAt, COMPLETION_AFTER);
            return schedule;
        }

        /** 정원 4가 찬 CLOSED 일정. memberId가 그중 한 명이다. */
        private Schedule joinedFull() {
            Schedule schedule = joined(4);
            schedule.join(UUID.randomUUID(), joinAt, COMPLETION_AFTER);
            schedule.join(UUID.randomUUID(), joinAt, COMPLETION_AFTER);
            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
            return schedule;
        }

        private void assertLeaveRejected(Schedule schedule, UUID userId, LocalDateTime now,
                ScheduleErrorCode errorCode) {
            Snapshot before = Snapshot.of(schedule);

            assertInvalid(() -> schedule.leave(userId, now, COMPLETION_AFTER), errorCode);
            assertThat(Snapshot.of(schedule)).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("보는 사람의 역할")
    class Viewer {

        private final Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);

        @Test
        @DisplayName("로그인하지 않았으면(id null) GUEST")
        void guest() {
            assertThat(schedule.viewerRole(null)).isEqualTo(ViewerRole.GUEST);
        }

        @Test
        @DisplayName("개설자면 HOST")
        void host() {
            assertThat(schedule.viewerRole(HOST_ID)).isEqualTo(ViewerRole.HOST);
        }

        @Test
        @DisplayName("멤버로 확정 참여했으면 PARTICIPANT")
        void participant() {
            UUID userId = UUID.randomUUID();
            schedule.join(userId, NOW, COMPLETION_AFTER);

            assertThat(schedule.viewerRole(userId)).isEqualTo(ViewerRole.PARTICIPANT);
        }

        @Test
        @DisplayName("참여한 적이 없으면 NON_PARTICIPANT")
        void neverJoined() {
            assertThat(schedule.viewerRole(UUID.randomUUID())).isEqualTo(ViewerRole.NON_PARTICIPANT);
        }

        @Test
        @DisplayName("참여를 취소했으면 NON_PARTICIPANT")
        void left() {
            UUID userId = UUID.randomUUID();
            schedule.join(userId, NOW, COMPLETION_AFTER);
            schedule.leave(userId, NOW, COMPLETION_AFTER);

            assertThat(schedule.viewerRole(userId)).isEqualTo(ViewerRole.NON_PARTICIPANT);
        }

        @Test
        @DisplayName("확정 참여자 id는 개설자와 확정 멤버다. 참여를 취소한 사람은 빠진다")
        void confirmedUserIds() {
            UUID stayed = UUID.randomUUID();
            UUID left = UUID.randomUUID();
            schedule.join(stayed, NOW, COMPLETION_AFTER);
            schedule.join(left, NOW, COMPLETION_AFTER);
            schedule.leave(left, NOW, COMPLETION_AFTER);

            assertThat(schedule.confirmedUserIds()).containsExactlyInAnyOrder(HOST_ID, stayed);
        }
    }

    @Nested
    @DisplayName("참여자 명단")
    class ParticipantList {

        private final Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);

        @Test
        @DisplayName("개설자와 확정 멤버는 볼 수 있다")
        void hostAndConfirmedMemberCanView() {
            UUID member = UUID.randomUUID();
            schedule.join(member, NOW, COMPLETION_AFTER);

            schedule.validateParticipantListViewable(HOST_ID);
            schedule.validateParticipantListViewable(member);
        }

        @Test
        @DisplayName("참여한 적이 없으면 PARTICIPANT_LIST_FORBIDDEN")
        void neverJoinedCannotView() {
            assertInvalid(() -> schedule.validateParticipantListViewable(UUID.randomUUID()),
                    ScheduleErrorCode.PARTICIPANT_LIST_FORBIDDEN);
        }

        @Test
        @DisplayName("참여를 취소해 취소된 행만 있으면 PARTICIPANT_LIST_FORBIDDEN")
        void leftCannotView() {
            UUID left = UUID.randomUUID();
            schedule.join(left, NOW, COMPLETION_AFTER);
            schedule.leave(left, NOW, COMPLETION_AFTER);

            assertInvalid(() -> schedule.validateParticipantListViewable(left),
                    ScheduleErrorCode.PARTICIPANT_LIST_FORBIDDEN);
        }

        @ParameterizedTest
        @EnumSource(value = ScheduleStatus.class, names = {"CANCELED", "COMPLETED"})
        @DisplayName("일정이 취소·완료돼도 개설자와 확정 멤버는 볼 수 있다")
        void canceledOrCompletedScheduleCanBeViewed(ScheduleStatus status) {
            UUID member = UUID.randomUUID();
            schedule.join(member, NOW, COMPLETION_AFTER);
            ReflectionTestUtils.setField(schedule, "status", status);

            schedule.validateParticipantListViewable(HOST_ID);
            schedule.validateParticipantListViewable(member);
        }

        @Test
        @DisplayName("개설자가 맨 앞(개설자 행이 더 늦게 만들어졌어도), 그 뒤 멤버는 참여 일시 순이다")
        void hostFirstThenByCreatedAt() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            UUID third = UUID.randomUUID();
            schedule.join(third, NOW, COMPLETION_AFTER);
            schedule.join(first, NOW, COMPLETION_AFTER);
            schedule.join(second, NOW, COMPLETION_AFTER);
            joinedAt(HOST_ID, NOW.plusHours(1));
            joinedAt(first, NOW.plusMinutes(1));
            joinedAt(second, NOW.plusMinutes(2));
            joinedAt(third, NOW.plusMinutes(3));

            assertThat(listUserIds()).containsExactly(HOST_ID, first, second, third);
        }

        @Test
        @DisplayName("취소된 행과 삭제된 행은 빠진다")
        void excludesCanceledAndDeletedRows() {
            UUID stayed = UUID.randomUUID();
            UUID left = UUID.randomUUID();
            UUID deleted = UUID.randomUUID();
            schedule.join(stayed, NOW, COMPLETION_AFTER);
            schedule.join(left, NOW, COMPLETION_AFTER);
            schedule.join(deleted, NOW, COMPLETION_AFTER);
            schedule.leave(left, NOW, COMPLETION_AFTER);
            rowsOf(schedule, deleted).forEach(ScheduleParticipant::delete);
            joinedAt(HOST_ID, NOW);
            joinedAt(stayed, NOW.plusMinutes(1));
            joinedAt(left, NOW.plusMinutes(2));
            joinedAt(deleted, NOW.plusMinutes(3));

            assertThat(listUserIds()).containsExactly(HOST_ID, stayed);
        }

        @Test
        @DisplayName("참여 취소 후 다시 참여한 사람은 한 번만 나오고 새 행의 참여 일시 자리에 온다")
        void rejoinedAppearsOnceAtNewRow() {
            UUID rejoined = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            schedule.join(rejoined, NOW, COMPLETION_AFTER);
            schedule.leave(rejoined, NOW, COMPLETION_AFTER);
            schedule.join(other, NOW, COMPLETION_AFTER);
            schedule.join(rejoined, NOW, COMPLETION_AFTER);
            List<ScheduleParticipant> rejoinedRows = rowsOf(schedule, rejoined);
            ReflectionTestUtils.setField(rejoinedRows.get(0), "createdAt", NOW.plusMinutes(1));
            ReflectionTestUtils.setField(rejoinedRows.get(1), "createdAt", NOW.plusMinutes(3));
            joinedAt(HOST_ID, NOW);
            joinedAt(other, NOW.plusMinutes(2));

            assertThat(listUserIds()).containsExactly(HOST_ID, other, rejoined);
        }

        /** 그 사용자의 행 모두의 참여 일시를 정한다. Auditing이 채우는 값이라 직접 넣는다. */
        private void joinedAt(UUID userId, LocalDateTime createdAt) {
            rowsOf(schedule, userId).forEach(row -> ReflectionTestUtils.setField(row, "createdAt", createdAt));
        }

        private List<UUID> listUserIds() {
            return schedule.confirmedParticipantsInListOrder().stream()
                    .map(ScheduleParticipant::getUserId)
                    .toList();
        }
    }

    @Nested
    @DisplayName("누를 수 있는 동작")
    class Actions {

        /** 표시 상태별 상황. 같은 표시 상태라도 개설자의 일정 취소가 갈리는 경우를 나눈다. */
        enum Situation {

            /** 시작 1시간 전, 모집 중. */
            RECRUITING(DisplayStatus.RECRUITING),

            /** 정원이 차 마감된 시작 1시간 전. */
            CLOSED_FULL_BEFORE_START(DisplayStatus.RECRUITMENT_CLOSED),

            /** 시작 시각 정각. 저장된 모집 상태는 아직 RECRUITING(스케줄러 전)이다. */
            CLOSED_AT_START_BEFORE_SCHEDULER(DisplayStatus.RECRUITMENT_CLOSED),

            /** 시작 1시간 뒤. 저장된 모집 상태는 아직 RECRUITING(스케줄러 전)이다. */
            CLOSED_AFTER_START_BEFORE_SCHEDULER(DisplayStatus.RECRUITMENT_CLOSED),

            /** 시작 + 12시간. 저장된 상태는 아직 SCHEDULED(스케줄러 전)다. */
            COMPLETED(DisplayStatus.COMPLETED),

            /** 개설자가 취소한 일정, 시작 1시간 전. */
            CANCELED(DisplayStatus.CANCELED);

            private final DisplayStatus displayStatus;

            Situation(DisplayStatus displayStatus) {
                this.displayStatus = displayStatus;
            }

            Schedule schedule() {
                Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);
                if (this == CLOSED_FULL_BEFORE_START) {
                    ReflectionTestUtils.setField(schedule, "recruitmentStatus", RecruitmentStatus.CLOSED);
                }
                if (this == CANCELED) {
                    ReflectionTestUtils.setField(schedule, "status", ScheduleStatus.CANCELED);
                }
                return schedule;
            }

            LocalDateTime now() {
                return switch (this) {
                    case RECRUITING, CLOSED_FULL_BEFORE_START, CANCELED -> START_AT.minusHours(1);
                    case CLOSED_AT_START_BEFORE_SCHEDULER -> START_AT;
                    case CLOSED_AFTER_START_BEFORE_SCHEDULER -> START_AT.plusHours(1);
                    case COMPLETED -> START_AT.plusHours(12);
                };
            }
        }

        static Stream<Arguments> table() {
            List<ScheduleAction> none = List.of();
            List<ScheduleAction> login = List.of(ScheduleAction.LOGIN_TO_JOIN);
            List<ScheduleAction> join = List.of(ScheduleAction.JOIN);
            List<ScheduleAction> leave = List.of(ScheduleAction.CANCEL_PARTICIPATION);
            List<ScheduleAction> cancel = List.of(ScheduleAction.CANCEL_SCHEDULE);
            return Stream.of(
                    // 상황, GUEST, NON_PARTICIPANT, PARTICIPANT, HOST
                    row(Situation.RECRUITING, login, join, leave, cancel),
                    row(Situation.CLOSED_FULL_BEFORE_START, none, none, leave, cancel),
                    row(Situation.CLOSED_AT_START_BEFORE_SCHEDULER, none, none, leave, none),
                    row(Situation.CLOSED_AFTER_START_BEFORE_SCHEDULER, none, none, leave, none),
                    row(Situation.COMPLETED, none, none, none, none),
                    row(Situation.CANCELED, none, none, none, none))
                    .flatMap(arguments -> arguments);
        }

        private static Stream<Arguments> row(Situation situation, List<ScheduleAction> guest,
                List<ScheduleAction> nonParticipant, List<ScheduleAction> participant, List<ScheduleAction> host) {
            return Stream.of(
                    Arguments.of(situation, ViewerRole.GUEST, guest),
                    Arguments.of(situation, ViewerRole.NON_PARTICIPANT, nonParticipant),
                    Arguments.of(situation, ViewerRole.PARTICIPANT, participant),
                    Arguments.of(situation, ViewerRole.HOST, host));
        }

        @ParameterizedTest(name = "{0} × {1} → {2}")
        @MethodSource("table")
        @DisplayName("역할 × 표시 상태 조합마다 누를 수 있는 동작")
        void byRoleAndDisplayStatus(Situation situation, ViewerRole role, List<ScheduleAction> expected) {
            Schedule schedule = situation.schedule();

            assertThat(schedule.displayStatus(situation.now(), COMPLETION_AFTER)).isEqualTo(situation.displayStatus);
            assertThat(schedule.availableActions(role, situation.now(), COMPLETION_AFTER))
                    .containsExactlyElementsOf(expected);
        }

        @Test
        @DisplayName("시작 시각이 지났는데 저장된 모집 상태가 RECRUITING이면 표시 상태는 RECRUITMENT_CLOSED이고 GUEST·NON_PARTICIPANT는 누를 것이 없다")
        void afterStartBeforeScheduler() {
            Schedule schedule = create(TITLE, DESCRIPTION, START_AT, CAPACITY, NOW);
            LocalDateTime now = START_AT.plusMinutes(1);

            assertThat(schedule.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.RECRUITING);
            assertThat(schedule.displayStatus(now, COMPLETION_AFTER)).isEqualTo(DisplayStatus.RECRUITMENT_CLOSED);
            assertThat(schedule.availableActions(ViewerRole.GUEST, now, COMPLETION_AFTER)).isEmpty();
            assertThat(schedule.availableActions(ViewerRole.NON_PARTICIPANT, now, COMPLETION_AFTER)).isEmpty();
        }
    }

    private static List<ScheduleParticipant> rowsOf(Schedule schedule, UUID userId) {
        return schedule.getParticipants().stream()
                .filter(participant -> participant.getUserId().equals(userId))
                .toList();
    }

    /** 거부됐을 때 바뀌면 안 되는 것: 참여 행(사용자·역할·상태), 확정 인원, 모집 상태. */
    private record Snapshot(List<String> rows, int confirmedCount, RecruitmentStatus recruitmentStatus) {

        static Snapshot of(Schedule schedule) {
            return new Snapshot(schedule.getParticipants().stream()
                    .map(participant -> participant.getUserId() + ":" + participant.getRole() + ":"
                            + participant.getStatus())
                    .toList(), schedule.getConfirmedCount(), schedule.getRecruitmentStatus());
        }
    }

    private static Schedule create(String title, String description, LocalDateTime startAt, int capacity,
            LocalDateTime now) {
        return Schedule.create(HOST_ID, CATEGORY_ID, title, description, DurationType.NORMAL, startAt, capacity,
                PLACE_ID, PlaceSnapshot.of("보드게임카페", "서울특별시 마포구 와우산로 1", "2층",
                        new BigDecimal("37.5500000"), new BigDecimal("126.9200000"), "11440"),
                RULE, now);
    }

    private static void assertInvalid(Runnable action, ScheduleErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
