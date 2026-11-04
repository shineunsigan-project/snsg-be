package com.shineunsigan.snsg.schedule.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.constants.HostType;
import com.shineunsigan.snsg.schedule.domain.constants.ParticipantRole;
import com.shineunsigan.snsg.schedule.domain.constants.ParticipationStatus;
import com.shineunsigan.snsg.schedule.domain.constants.RecruitmentStatus;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleStatus;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

/**
 * 개설의 형식 규칙(제목·설명·시작 일시·인원)과 화면 표시 상태 판정을 검증한다.
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
