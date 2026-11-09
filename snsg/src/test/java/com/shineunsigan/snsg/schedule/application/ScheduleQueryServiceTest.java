package com.shineunsigan.snsg.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.CommonErrorCode;
import com.shineunsigan.snsg.schedule.application.dto.command.ListRecruitingSchedulesCommand;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleCardPageResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleCardResult;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;
import com.shineunsigan.snsg.schedule.domain.model.ActivityCategory;
import com.shineunsigan.snsg.schedule.domain.model.PlaceSnapshot;
import com.shineunsigan.snsg.schedule.domain.model.RecruitingScheduleFilter;
import com.shineunsigan.snsg.schedule.domain.model.Schedule;
import com.shineunsigan.snsg.schedule.domain.model.ScheduleCursor;
import com.shineunsigan.snsg.schedule.domain.repository.ActivityCategoryRepository;
import com.shineunsigan.snsg.schedule.domain.repository.ScheduleRepository;

/**
 * 목록의 페이지 크기(기본·상한·하한), 날짜 필터 해석과 오류 순서, 다음 페이지 판정(한 건 더 읽기), 카드 변환을 본다.
 * 쿼리 조건 자체는 ScheduleRepositoryImplTest가 실제 PostgreSQL에서 본다.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleQueryServiceTest {

    private static final ScheduleProperties PROPERTIES = new ScheduleProperties(4, 24, Duration.ofHours(1), 30,
            Duration.ofHours(12), Duration.ofHours(12), 20, 50);
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final PlaceSnapshot PLACE = PlaceSnapshot.of("보드게임카페", "서울특별시 마포구 와우산로 1", "2층",
            new BigDecimal("37.5500000"), new BigDecimal("126.9200000"), "11440");

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ActivityCategoryRepository activityCategoryRepository;

    private ScheduleQueryService scheduleQueryService;

    @BeforeEach
    void setUp() {
        scheduleQueryService = new ScheduleQueryService(scheduleRepository, activityCategoryRepository, PROPERTIES);
    }

    // 페이지 크기

    @Test
    @DisplayName("size를 생략하면 20건, 51이면 50건, 0이면 1건을 한 페이지로 보고 한 건 더 읽는다")
    void pageSizeIsClamped() {
        assertRecruitingLimit(null, 21);
        assertRecruitingLimit(51, 51);
        assertRecruitingLimit(0, 2);
    }

    @Test
    @DisplayName("참여한·개설한 목록도 같은 기준으로 size를 맞춘다")
    void pageSizeIsClampedForMyLists() {
        scheduleQueryService.findParticipating(USER_ID, null, 51);
        scheduleQueryService.findHosted(USER_ID, null, null);

        verify(scheduleRepository).findParticipating(eq(USER_ID), any(), isNull(), eq(51));
        verify(scheduleRepository).findHosted(eq(USER_ID), any(), isNull(), eq(21));
    }

    // 날짜 필터

    @Test
    @DisplayName("startDate만 보내면 그날 00:00부터 다음 날 00:00 전까지로 조회한다")
    void startDateOnlyIsOneDay() {
        RecruitingScheduleFilter filter = recruitingFilter(LocalDate.of(2026, 11, 14), null);

        assertThat(filter.startFrom()).isEqualTo(LocalDateTime.of(2026, 11, 14, 0, 0));
        assertThat(filter.startBefore()).isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0));
    }

    @Test
    @DisplayName("endDate만 보내면 그날 하루로 조회한다")
    void endDateOnlyIsOneDay() {
        RecruitingScheduleFilter filter = recruitingFilter(null, LocalDate.of(2026, 11, 15));

        assertThat(filter.startFrom()).isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0));
        assertThat(filter.startBefore()).isEqualTo(LocalDateTime.of(2026, 11, 16, 0, 0));
    }

    @Test
    @DisplayName("날짜 범위는 시작 날짜 00:00부터 끝 날짜 다음 날 00:00 전까지다")
    void dateRangeIncludesBothEnds() {
        RecruitingScheduleFilter filter = recruitingFilter(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15));

        assertThat(filter.startFrom()).isEqualTo(LocalDateTime.of(2026, 11, 14, 0, 0));
        assertThat(filter.startBefore()).isEqualTo(LocalDateTime.of(2026, 11, 16, 0, 0));
    }

    @Test
    @DisplayName("날짜를 생략하면 날짜 조건 없이 조회한다")
    void noDateNoCondition() {
        RecruitingScheduleFilter filter = recruitingFilter(null, null);

        assertThat(filter.startFrom()).isNull();
        assertThat(filter.startBefore()).isNull();
    }

    @Test
    @DisplayName("활동을 21개 보내면 INVALID_REQUEST이고 일정을 조회하지 않는다")
    void tooManyCategories() {
        List<UUID> categoryIds = IntStream.range(0, 21).mapToObj(index -> UUID.randomUUID()).toList();

        assertThatThrownBy(() -> scheduleQueryService.findRecruiting(
                new ListRecruitingSchedulesCommand(categoryIds, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    @DisplayName("끝 날짜가 시작 날짜보다 앞서면 INVALID_DATE_FILTER이고 일정을 조회하지 않는다")
    void reversedDates() {
        assertError(() -> scheduleQueryService.findRecruiting(command(LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 14), null, null)), ScheduleErrorCode.INVALID_DATE_FILTER);
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    @DisplayName("날짜와 커서가 둘 다 틀리면 날짜를 먼저 봐서 INVALID_DATE_FILTER")
    void dateCheckedBeforeCursor() {
        assertError(() -> scheduleQueryService.findRecruiting(command(LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 14), "not base64!", null)), ScheduleErrorCode.INVALID_DATE_FILTER);
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    @DisplayName("해석할 수 없는 커서면 INVALID_CURSOR이고 일정을 조회하지 않는다")
    void invalidCursor() {
        assertError(() -> scheduleQueryService.findHosted(USER_ID, "not base64!", null),
                ScheduleErrorCode.INVALID_CURSOR);
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    @DisplayName("커서를 보내면 그 (시작 시각, id) 뒤부터 조회한다. 빈 문자열은 처음부터다")
    void cursorIsDecoded() {
        ScheduleCursor cursor = new ScheduleCursor(LocalDateTime.of(2026, 11, 14, 19, 30), UUID.randomUUID());

        scheduleQueryService.findParticipating(USER_ID, cursor.encode(), null);
        scheduleQueryService.findHosted(USER_ID, "", null);

        verify(scheduleRepository).findParticipating(eq(USER_ID), any(), eq(cursor), anyInt());
        verify(scheduleRepository).findHosted(eq(USER_ID), any(), isNull(), anyInt());
    }

    // 다음 페이지

    @Test
    @DisplayName("size + 1건이 오면 size건만 주고 nextCursor는 마지막 item의 (시작 시각, id)다")
    void hasNextPage() {
        List<Schedule> schedules = schedules(3);
        when(scheduleRepository.findHosted(eq(USER_ID), any(), any(), eq(3))).thenReturn(schedules);

        ScheduleCardPageResult page = scheduleQueryService.findHosted(USER_ID, null, 2);

        assertThat(page.items()).extracting(ScheduleCardResult::id)
                .containsExactly(schedules.get(0).getId(), schedules.get(1).getId());
        assertThat(page.nextCursor()).isEqualTo(
                new ScheduleCursor(schedules.get(1).getStartAt(), schedules.get(1).getId()).encode());
    }

    @Test
    @DisplayName("size건 이하가 오면 모두 주고 nextCursor는 null이다")
    void lastPage() {
        List<Schedule> schedules = schedules(2);
        when(scheduleRepository.findHosted(eq(USER_ID), any(), any(), eq(3))).thenReturn(schedules);

        ScheduleCardPageResult page = scheduleQueryService.findHosted(USER_ID, null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    // 카드

    @Test
    @DisplayName("카드에 활동 이름(비활성 활동 포함), 지역 코드, 남은 자리, 표시 상태를 담는다. 활동을 찾지 못하면 이름은 빈 문자열")
    void cardFields() {
        Schedule known = schedule(CATEGORY_ID, 0);
        UUID missingCategoryId = UUID.randomUUID();
        Schedule unknown = schedule(missingCategoryId, 1);
        when(scheduleRepository.findHosted(eq(USER_ID), any(), any(), anyInt())).thenReturn(List.of(known, unknown));
        ActivityCategory inactiveCategory = mock(ActivityCategory.class);
        when(inactiveCategory.getId()).thenReturn(CATEGORY_ID);
        when(inactiveCategory.getName()).thenReturn("보드게임");
        when(activityCategoryRepository.findAllNotDeletedByIds(any())).thenReturn(List.of(inactiveCategory));

        List<ScheduleCardResult> items = scheduleQueryService.findHosted(USER_ID, null, null).items();

        assertThat(items.get(0).categoryName()).isEqualTo("보드게임");
        assertThat(items.get(0).regionCode()).isEqualTo("11440");
        assertThat(items.get(0).remainingSeats()).isEqualTo(5);
        assertThat(items.get(0).displayStatus()).isEqualTo(DisplayStatus.RECRUITING);
        assertThat(items.get(1).categoryName()).isEmpty();
    }

    @Test
    @DisplayName("빈 목록 조건만 보내면(빈 값) 일정이 없다")
    void blankFilterValuesMatchNothing() {
        ArgumentCaptor<RecruitingScheduleFilter> filter = ArgumentCaptor.forClass(RecruitingScheduleFilter.class);

        scheduleQueryService.findRecruiting(new ListRecruitingSchedulesCommand(Arrays.asList((UUID) null),
                List.of(" "), null, null, null, null));

        verify(scheduleRepository).findRecruiting(filter.capture(), any(), any(), anyInt());
        assertThat(filter.getValue().matchesNothing()).isTrue();
    }

    private void assertRecruitingLimit(Integer size, int expectedLimit) {
        scheduleQueryService.findRecruiting(command(null, null, null, size));
        verify(scheduleRepository).findRecruiting(any(), any(), isNull(), eq(expectedLimit));
    }

    private RecruitingScheduleFilter recruitingFilter(LocalDate startDate, LocalDate endDate) {
        scheduleQueryService.findRecruiting(command(startDate, endDate, null, null));
        ArgumentCaptor<RecruitingScheduleFilter> filter = ArgumentCaptor.forClass(RecruitingScheduleFilter.class);
        verify(scheduleRepository).findRecruiting(filter.capture(), any(), any(), anyInt());
        return filter.getValue();
    }

    private static ListRecruitingSchedulesCommand command(LocalDate startDate, LocalDate endDate, String cursor,
            Integer size) {
        return new ListRecruitingSchedulesCommand(List.of(CATEGORY_ID), null, startDate, endDate, cursor, size);
    }

    private static List<Schedule> schedules(int count) {
        return IntStream.range(0, count).mapToObj(index -> schedule(CATEGORY_ID, index)).toList();
    }

    /** 이틀 뒤 저녁에 시작하는 정원 6, 확정 1의 모집 중 일정. index만큼 30분씩 늦다. id를 직접 넣는다. */
    private static Schedule schedule(UUID categoryId, int index) {
        LocalDateTime startAt = LocalDate.now().plusDays(2).atTime(19, 0).plusMinutes(30L * index);
        Schedule schedule = Schedule.create(UUID.randomUUID(), categoryId, "보드게임 같이 해요", "처음 오시는 분도 환영해요.",
                DurationType.NORMAL, startAt, 6, UUID.randomUUID(), PLACE, PROPERTIES.creationRule(),
                LocalDateTime.now());
        ReflectionTestUtils.setField(schedule, "id", UUID.randomUUID());
        return schedule;
    }

    private static void assertError(ThrowingCallable call,
            ScheduleErrorCode errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
