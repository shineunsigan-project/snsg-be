package com.shineunsigan.snsg.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.shineunsigan.snsg.common.config.JpaAuditingConfig;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.place.application.PlaceQueryService;
import com.shineunsigan.snsg.place.infrastructure.persistence.PlaceRepositoryImpl;
import com.shineunsigan.snsg.schedule.application.dto.command.CreateScheduleCommand;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleDetailQueryResult;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleStatus;
import com.shineunsigan.snsg.schedule.domain.constants.ViewerRole;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;
import com.shineunsigan.snsg.schedule.domain.model.ActivityCategory;
import com.shineunsigan.snsg.schedule.domain.model.PlaceSnapshot;
import com.shineunsigan.snsg.schedule.domain.model.Schedule;
import com.shineunsigan.snsg.schedule.domain.repository.ActivityCategoryRepository;
import com.shineunsigan.snsg.schedule.domain.repository.ScheduleRepository;
import com.shineunsigan.snsg.schedule.infrastructure.client.SchedulePlaceAdapter;
import com.shineunsigan.snsg.schedule.infrastructure.persistence.ActivityCategoryRepositoryImpl;
import com.shineunsigan.snsg.schedule.infrastructure.persistence.ScheduleRepositoryImpl;

import jakarta.persistence.EntityManager;

/**
 * 개설 트랜잭션의 순서(잠금 → 활동 재확인 → 겹침 검사 → 저장)와 그 결과를 검증한다.
 * <p>
 * 흐름은 Mockito로, 동시 개설과 저장 결과는 실제 PostgreSQL로 본다.
 * 활동은 마이그레이션의 보드게임 행(고정 id)을 쓴다. 사용자 id는 테스트마다 무작위다(DB를 모든 워크트리가 공유한다).
 */
class ScheduleCommandServiceTest {

    /** V6__create_activity_category.sql의 보드게임(BOARD_GAME_CAFE). */
    private static final UUID BOARD_GAME_ID = UUID.fromString("01a19cc6-3d80-7000-8000-000000000001");
    private static final PlaceSnapshot PLACE = PlaceSnapshot.of("보드게임카페", "서울특별시 마포구 와우산로 1", "2층",
            new BigDecimal("37.5500000"), new BigDecimal("126.9200000"), "11440");

    /** 지금 + 1시간 이후, 30일 이내의 30분 단위 시각. */
    private static LocalDateTime futureStartAt() {
        return LocalDateTime.now().toLocalDate().plusDays(2).atTime(10, 0);
    }

    private static CreateScheduleCommand command(UUID userId, UUID placeId, LocalDateTime startAt) {
        return new CreateScheduleCommand(userId, BOARD_GAME_ID, placeId, "보드게임 같이 해요", DurationType.NORMAL,
                "처음 오시는 분도 환영해요.", startAt, 6);
    }

    private static ScheduleErrorCode errorCodeOf(Throwable throwable) {
        return (ScheduleErrorCode) ((BusinessException) throwable).getErrorCode();
    }

    @Nested
    @DisplayName("개설 흐름")
    @ExtendWith(MockitoExtension.class)
    class Flow {

        private static final ScheduleProperties PROPERTIES = new ScheduleProperties(4, 24, Duration.ofHours(1), 30,
                Duration.ofHours(12), Duration.ofHours(12), 20, 50, Duration.ofHours(24));

        @Mock
        private ScheduleRepository scheduleRepository;

        @Mock
        private ActivityCategoryRepository activityCategoryRepository;

        private ScheduleCommandService scheduleCommandService;

        private final UUID userId = UUID.randomUUID();
        private final LocalDateTime startAt = futureStartAt();

        @BeforeEach
        void setUp() {
            scheduleCommandService = new ScheduleCommandService(scheduleRepository, activityCategoryRepository,
                    PROPERTIES);
        }

        @Test
        @DisplayName("사용자를 잠근 뒤 활동을 다시 확인하고 겹침을 검사한 다음 저장한다")
        void locksBeforeOverlapCheck() {
            when(activityCategoryRepository.findActiveById(BOARD_GAME_ID))
                    .thenReturn(Optional.of(mock(ActivityCategory.class)));
            when(scheduleRepository.existsOverlapping(userId, startAt, Duration.ofHours(12))).thenReturn(false);
            when(scheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            scheduleCommandService.create(command(userId, UUID.randomUUID(), startAt), PLACE);

            InOrder order = inOrder(scheduleRepository, activityCategoryRepository);
            order.verify(scheduleRepository).lockUser(userId);
            order.verify(activityCategoryRepository).findActiveById(BOARD_GAME_ID);
            order.verify(scheduleRepository).existsOverlapping(userId, startAt, Duration.ofHours(12));
            order.verify(scheduleRepository).save(any(Schedule.class));
        }

        @Test
        @DisplayName("12시간 안에 확정 참여한 일정이 있으면 SCHEDULE_TIME_CONFLICT이고 저장하지 않는다")
        void overlapIsRejected() {
            when(activityCategoryRepository.findActiveById(BOARD_GAME_ID))
                    .thenReturn(Optional.of(mock(ActivityCategory.class)));
            when(scheduleRepository.existsOverlapping(eq(userId), eq(startAt), any())).thenReturn(true);

            assertThatThrownBy(() -> scheduleCommandService.create(command(userId, UUID.randomUUID(), startAt), PLACE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ScheduleCommandServiceTest::errorCodeOf)
                    .isEqualTo(ScheduleErrorCode.SCHEDULE_TIME_CONFLICT);
            verify(scheduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("트랜잭션 안에서 활동이 없거나 비활성이면 INVALID_CATEGORY이고 겹침 검사·저장을 하지 않는다")
        void inactiveCategoryIsRejected() {
            when(activityCategoryRepository.findActiveById(BOARD_GAME_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleCommandService.create(command(userId, UUID.randomUUID(), startAt), PLACE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ScheduleCommandServiceTest::errorCodeOf)
                    .isEqualTo(ScheduleErrorCode.INVALID_CATEGORY);
            verify(scheduleRepository).lockUser(userId);
            verify(scheduleRepository, never()).existsOverlapping(any(), any(), any());
            verify(scheduleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("참여 흐름")
    @ExtendWith(MockitoExtension.class)
    class JoinFlow {

        @Mock
        private ScheduleRepository scheduleRepository;

        @Mock
        private ActivityCategoryRepository activityCategoryRepository;

        private ScheduleCommandService scheduleCommandService;

        private final UUID hostId = UUID.randomUUID();
        private final UUID userId = UUID.randomUUID();
        private final Schedule schedule = openSchedule(hostId);
        private final UUID scheduleId = UUID.randomUUID();

        @BeforeEach
        void setUp() {
            scheduleCommandService = new ScheduleCommandService(scheduleRepository, activityCategoryRepository,
                    Flow.PROPERTIES);
        }

        @Test
        @DisplayName("사용자를 잠근 뒤 일정을 잠가 읽고, 그다음 겹침을 검사한다")
        void locksUserThenScheduleThenChecksOverlap() {
            when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));

            scheduleCommandService.join(scheduleId, userId);

            InOrder order = inOrder(scheduleRepository);
            order.verify(scheduleRepository).lockUser(userId);
            order.verify(scheduleRepository).findByIdForUpdate(scheduleId);
            order.verify(scheduleRepository).existsOverlapping(userId, schedule.getStartAt(), Duration.ofHours(12));
            assertThat(schedule.getConfirmedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("일정이 없으면 SCHEDULE_NOT_FOUND이고 겹침 검사를 하지 않는다")
        void scheduleNotFound() {
            when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleCommandService.join(scheduleId, userId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ScheduleCommandServiceTest::errorCodeOf)
                    .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);
            verify(scheduleRepository, never()).existsOverlapping(any(), any(), any());
        }

        @Test
        @DisplayName("12시간 안에 확정 참여한 일정이 있으면 SCHEDULE_TIME_CONFLICT이고 참여 행이 더해지지 않는다")
        void overlapIsRejected() {
            when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));
            when(scheduleRepository.existsOverlapping(eq(userId), eq(schedule.getStartAt()), any())).thenReturn(true);

            assertThatThrownBy(() -> scheduleCommandService.join(scheduleId, userId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ScheduleCommandServiceTest::errorCodeOf)
                    .isEqualTo(ScheduleErrorCode.SCHEDULE_TIME_CONFLICT);
            assertThat(schedule.getParticipants()).hasSize(1);
            assertThat(schedule.getConfirmedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("개설자가 참여하면 HOST_CANNOT_JOIN이고 겹침 검사를 하지 않는다")
        void hostIsRejectedBeforeOverlapCheck() {
            when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));

            assertThatThrownBy(() -> scheduleCommandService.join(scheduleId, hostId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ScheduleCommandServiceTest::errorCodeOf)
                    .isEqualTo(ScheduleErrorCode.HOST_CANNOT_JOIN);
            verify(scheduleRepository, never()).existsOverlapping(any(), any(), any());
        }

        @Test
        @DisplayName("이미 참여한 사용자는 ALREADY_JOINED이고 겹침 검사를 하지 않는다")
        void alreadyJoinedIsRejectedBeforeOverlapCheck() {
            schedule.join(userId, LocalDateTime.now(), Duration.ofHours(12));
            when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));

            assertThatThrownBy(() -> scheduleCommandService.join(scheduleId, userId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ScheduleCommandServiceTest::errorCodeOf)
                    .isEqualTo(ScheduleErrorCode.ALREADY_JOINED);
            verify(scheduleRepository, never()).existsOverlapping(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("참여 취소 흐름")
    @ExtendWith(MockitoExtension.class)
    class LeaveFlow {

        @Mock
        private ScheduleRepository scheduleRepository;

        @Mock
        private ActivityCategoryRepository activityCategoryRepository;

        private ScheduleCommandService scheduleCommandService;

        private final UUID userId = UUID.randomUUID();
        private final UUID scheduleId = UUID.randomUUID();

        @BeforeEach
        void setUp() {
            scheduleCommandService = new ScheduleCommandService(scheduleRepository, activityCategoryRepository,
                    Flow.PROPERTIES);
        }

        @Test
        @DisplayName("일정이 없으면 SCHEDULE_NOT_FOUND이고 사용자를 잠그지 않는다")
        void scheduleNotFound() {
            when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleCommandService.leave(scheduleId, userId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ScheduleCommandServiceTest::errorCodeOf)
                    .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);
            verify(scheduleRepository, never()).lockUser(any());
        }

        @Test
        @DisplayName("일정을 잠가 읽고 참여를 취소한다. 사용자를 잠그지 않는다")
        void leavesWithoutUserLock() {
            Schedule schedule = openSchedule(UUID.randomUUID());
            schedule.join(userId, LocalDateTime.now(), Duration.ofHours(12));
            when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));

            scheduleCommandService.leave(scheduleId, userId);

            assertThat(schedule.getConfirmedCount()).isEqualTo(1);
            verify(scheduleRepository, never()).lockUser(any());
        }
    }

    @Nested
    @DisplayName("일정 전체 취소 흐름")
    @ExtendWith(MockitoExtension.class)
    class CancelFlow {

        @Mock
        private ScheduleRepository scheduleRepository;

        @Mock
        private ActivityCategoryRepository activityCategoryRepository;

        private ScheduleCommandService scheduleCommandService;

        private final UUID hostId = UUID.randomUUID();
        private final UUID scheduleId = UUID.randomUUID();

        @BeforeEach
        void setUp() {
            scheduleCommandService = new ScheduleCommandService(scheduleRepository, activityCategoryRepository,
                    Flow.PROPERTIES);
        }

        @Test
        @DisplayName("일정이 없으면 SCHEDULE_NOT_FOUND이고 사용자를 잠그지 않는다")
        void scheduleNotFound() {
            when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleCommandService.cancel(scheduleId, hostId, "사유"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ScheduleCommandServiceTest::errorCodeOf)
                    .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);
            verify(scheduleRepository, never()).lockUser(any());
        }

        @Test
        @DisplayName("일정을 잠가 읽고 취소한다. 사용자를 잠그지 않고 잠금 없는 조회도 부르지 않는다")
        void cancelsWithScheduleLockOnly() {
            Schedule schedule = openSchedule(hostId);
            when(scheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));

            scheduleCommandService.cancel(scheduleId, hostId, "개인 사정으로 취소해요.");

            assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.CANCELED);
            verify(scheduleRepository).findByIdForUpdate(scheduleId);
            verify(scheduleRepository, never()).lockUser(any());
            verify(scheduleRepository, never()).findById(any());
        }
    }

    /** 이틀 뒤에 시작하는 모집 중 일정(정원 6). */
    private static Schedule openSchedule(UUID hostId) {
        return Schedule.create(hostId, BOARD_GAME_ID, "보드게임 같이 해요", "처음 오시는 분도 환영해요.", DurationType.NORMAL,
                futureStartAt(), 6, UUID.randomUUID(), PLACE, Flow.PROPERTIES.creationRule(), LocalDateTime.now());
    }

    /**
     * 테스트 트랜잭션을 끄고 스레드가 각자 커밋한다. 커밋한 행(이 사용자가 연 일정과 그 참여 행·취소 행)은 끝나면 지운다.
     */
    @Nested
    @DisplayName("동시 요청")
    @DataJpaTest
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    @Import({ScheduleCommandService.class, ScheduleRepositoryImpl.class, ActivityCategoryRepositoryImpl.class,
            JpaAuditingConfig.class})
    @EnableConfigurationProperties(ScheduleProperties.class)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class Concurrency {

        @Autowired
        private ScheduleCommandService scheduleCommandService;

        @Autowired
        private EntityManager entityManager;

        @Autowired
        private PlatformTransactionManager transactionManager;

        private final UUID userId = UUID.randomUUID();

        @Autowired
        private ScheduleRepository scheduleRepository;

        @AfterEach
        void deleteCommittedRows() {
            SecurityContextHolder.clearContext();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                DELETE FROM schedule_cancellations
                                WHERE schedule_id IN (SELECT id FROM schedules WHERE host_user_id = ?)""")
                        .setParameter(1, userId)
                        .executeUpdate();
                entityManager.createNativeQuery("""
                                DELETE FROM schedule_participants
                                WHERE schedule_id IN (SELECT id FROM schedules WHERE host_user_id = ?)""")
                        .setParameter(1, userId)
                        .executeUpdate();
                entityManager.createNativeQuery("DELETE FROM schedules WHERE host_user_id = ?")
                        .setParameter(1, userId)
                        .executeUpdate();
            });
        }

        @Test
        @DisplayName("같은 사용자가 같은 시작 시각으로 동시에 두 번 열면 하나만 생기고 다른 하나는 SCHEDULE_TIME_CONFLICT")
        void onlyOneOfConcurrentCreatesSucceeds() throws Exception {
            LocalDateTime startAt = futureStartAt();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<UUID> create = () -> {
                ready.countDown();
                start.await();
                return scheduleCommandService.create(command(userId, UUID.randomUUID(), startAt), PLACE);
            };

            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<UUID> created = new ArrayList<>();
            List<Throwable> failures = new ArrayList<>();
            try {
                List<Future<UUID>> futures = List.of(executor.submit(create), executor.submit(create));
                ready.await();
                start.countDown();
                for (Future<UUID> future : futures) {
                    try {
                        created.add(future.get(30, TimeUnit.SECONDS));
                    } catch (ExecutionException exception) {
                        failures.add(exception.getCause());
                    }
                }
            } finally {
                executor.shutdownNow();
            }

            assertThat(created).hasSize(1);
            assertThat(failures).singleElement()
                    .isInstanceOf(BusinessException.class)
                    .extracting(ScheduleCommandServiceTest::errorCodeOf)
                    .isEqualTo(ScheduleErrorCode.SCHEDULE_TIME_CONFLICT);
            assertThat(count("SELECT COUNT(*) FROM schedules WHERE host_user_id = ?")).isEqualTo(1);
            assertThat(count("""
                    SELECT COUNT(*) FROM schedule_participants
                    WHERE user_id = ? AND role = 'HOST' AND status = 'CONFIRMED'""")).isEqualTo(1);
        }

        @Test
        @DisplayName("마지막 한 자리에 서로 다른 두 사용자가 동시에 참여하면 한 명만 성공하고 다른 한 명은 RECRUIT_CLOSED")
        void onlyOneOfConcurrentJoinsForLastSeatSucceeds() throws Exception {
            UUID scheduleId = createScheduleWithCapacity(4);
            scheduleCommandService.join(scheduleId, UUID.randomUUID());
            scheduleCommandService.join(scheduleId, UUID.randomUUID());
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<UUID> joinAsNewUser = () -> {
                UUID memberId = UUID.randomUUID();
                ready.countDown();
                start.await();
                scheduleCommandService.join(scheduleId, memberId);
                return memberId;
            };

            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<UUID> joined = new ArrayList<>();
            List<Throwable> failures = new ArrayList<>();
            try {
                List<Future<UUID>> futures = List.of(executor.submit(joinAsNewUser), executor.submit(joinAsNewUser));
                ready.await();
                start.countDown();
                for (Future<UUID> future : futures) {
                    try {
                        joined.add(future.get(30, TimeUnit.SECONDS));
                    } catch (ExecutionException exception) {
                        failures.add(exception.getCause());
                    }
                }
            } finally {
                executor.shutdownNow();
            }

            assertThat(joined).hasSize(1);
            assertThat(failures).singleElement()
                    .isInstanceOf(BusinessException.class)
                    .extracting(ScheduleCommandServiceTest::errorCodeOf)
                    .isEqualTo(ScheduleErrorCode.RECRUIT_CLOSED);
            assertThat(countBySchedule("""
                    SELECT COUNT(*) FROM schedules
                    WHERE id = ? AND confirmed_count = 4 AND recruitment_status = 'CLOSED'""", scheduleId))
                    .isEqualTo(1);
            assertThat(countBySchedule("""
                    SELECT COUNT(*) FROM schedule_participants
                    WHERE schedule_id = ? AND status = 'CONFIRMED'""", scheduleId)).isEqualTo(4);
        }

        @Test
        @DisplayName("참여 → 참여 취소 → 재참여하면 그 사용자의 행이 둘(CANCELED 1, CONFIRMED 1)로 저장되고 확정 인원이 맞다")
        void rejoinSavesNewRow() {
            UUID scheduleId = createScheduleWithCapacity(4);
            UUID memberId = UUID.randomUUID();

            scheduleCommandService.join(scheduleId, memberId);
            scheduleCommandService.leave(scheduleId, memberId);
            scheduleCommandService.join(scheduleId, memberId);

            assertThat(countBySchedule("""
                    SELECT COUNT(*) FROM schedule_participants
                    WHERE schedule_id = ? AND user_id = ? AND status = 'CANCELED'""", scheduleId, memberId))
                    .isEqualTo(1);
            assertThat(countBySchedule("""
                    SELECT COUNT(*) FROM schedule_participants
                    WHERE schedule_id = ? AND user_id = ? AND status = 'CONFIRMED'""", scheduleId, memberId))
                    .isEqualTo(1);
            assertThat(countBySchedule("SELECT COUNT(*) FROM schedules WHERE id = ? AND confirmed_count = 2",
                    scheduleId)).isEqualTo(1);
        }

        @Test
        @DisplayName("개설자가 취소하면 취소 행(created_by = 개설자, 사유는 앞뒤 공백을 뺀 값)이 커밋되고 일정은 CANCELED, 모집 상태·확정 인원·참여 행은 그대로다")
        void cancelCommitsCancellationByHost() {
            UUID scheduleId = createScheduleWithCapacity(4);
            scheduleCommandService.join(scheduleId, UUID.randomUUID());
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));

            scheduleCommandService.cancel(scheduleId, userId, "  개인 사정으로 취소해요.  ");

            Object[] cancellation = (Object[]) entityManager.createNativeQuery("""
                            SELECT created_by, reason FROM schedule_cancellations
                            WHERE schedule_id = ? AND is_deleted = FALSE""")
                    .setParameter(1, scheduleId)
                    .getSingleResult();
            assertThat(cancellation).containsExactly(userId.toString(), "개인 사정으로 취소해요.");
            assertThat(countBySchedule("""
                    SELECT COUNT(*) FROM schedules
                    WHERE id = ? AND status = 'CANCELED' AND recruitment_status = 'RECRUITING' AND confirmed_count = 2""",
                    scheduleId)).isEqualTo(1);
            assertThat(countBySchedule("SELECT COUNT(*) FROM schedule_participants WHERE schedule_id = ?",
                    scheduleId)).isEqualTo(2);
            assertThat(countBySchedule("""
                    SELECT COUNT(*) FROM schedule_participants
                    WHERE schedule_id = ? AND status = 'CONFIRMED'""", scheduleId)).isEqualTo(2);
        }

        @Test
        @DisplayName("개설자가 같은 일정을 동시에 두 번 취소하면 하나만 성공하고 다른 하나는 SCHEDULE_NOT_CANCELABLE, 취소 행은 하나다")
        void onlyOneOfConcurrentCancelsSucceeds() throws Exception {
            UUID scheduleId = createScheduleWithCapacity(4);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> cancel = () -> {
                ready.countDown();
                start.await();
                scheduleCommandService.cancel(scheduleId, userId, "개인 사정으로 취소해요.");
                return true;
            };

            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<Boolean> canceled = new ArrayList<>();
            List<Throwable> failures = new ArrayList<>();
            try {
                List<Future<Boolean>> futures = List.of(executor.submit(cancel), executor.submit(cancel));
                ready.await();
                start.countDown();
                for (Future<Boolean> future : futures) {
                    try {
                        canceled.add(future.get(30, TimeUnit.SECONDS));
                    } catch (ExecutionException exception) {
                        failures.add(exception.getCause());
                    }
                }
            } finally {
                executor.shutdownNow();
            }

            assertThat(canceled).hasSize(1);
            assertThat(failures).singleElement()
                    .isInstanceOf(BusinessException.class)
                    .extracting(ScheduleCommandServiceTest::errorCodeOf)
                    .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_CANCELABLE);
            assertThat(countBySchedule("SELECT COUNT(*) FROM schedule_cancellations WHERE schedule_id = ?",
                    scheduleId)).isEqualTo(1);
            assertThat(countBySchedule("SELECT COUNT(*) FROM schedules WHERE id = ? AND status = 'CANCELED'",
                    scheduleId)).isEqualTo(1);
        }

        @Test
        @DisplayName("취소한 일정을 상세 조회(잠금 없는 조회)로 다시 읽으면 CANCELED이고 취소 기록의 사유·취소 일시(취소 행 created_at)가 담긴다. 취소하지 않은 일정은 비어 있다")
        void canceledScheduleIsReadWithCancellation() {
            UUID canceledId = createScheduleWithCapacity(4);
            scheduleCommandService.cancel(canceledId, userId, " 개인 사정으로 취소해요. ");
            // 같은 시작 시각이라 취소한 뒤에 연다. 취소된 일정은 겹침 검사에서 빠진다.
            UUID openId = createScheduleWithCapacity(4);
            Object createdAt = entityManager.createNativeQuery(
                            "SELECT created_at FROM schedule_cancellations WHERE schedule_id = ?")
                    .setParameter(1, canceledId)
                    .getSingleResult();

            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                entityManager.flush();
                entityManager.clear();
                Schedule canceled = scheduleRepository.findById(canceledId).orElseThrow();
                Schedule open = scheduleRepository.findById(openId).orElseThrow();
                LocalDateTime now = LocalDateTime.now();

                assertThat(canceled.displayStatus(now, Flow.PROPERTIES.completionAfter()))
                        .isEqualTo(DisplayStatus.CANCELED);
                assertThat(ScheduleDetailQueryResult.of(canceled, "보드게임", DisplayStatus.CANCELED,
                        ViewerRole.GUEST, List.of()).cancellation())
                        .isEqualTo(new ScheduleDetailQueryResult.Cancellation("개인 사정으로 취소해요.",
                                toLocalDateTime(createdAt)));
                assertThat(open.cancellation()).isEmpty();
            });
        }

        /** 이 클래스의 사용자가 연 일정. 끝나면 deleteCommittedRows가 지운다. */
        private UUID createScheduleWithCapacity(int capacity) {
            return scheduleCommandService.create(new CreateScheduleCommand(userId, BOARD_GAME_ID, UUID.randomUUID(),
                    "보드게임 같이 해요", DurationType.NORMAL, "처음 오시는 분도 환영해요.", futureStartAt(), capacity), PLACE);
        }

        private long countBySchedule(String sql, Object... parameters) {
            var query = entityManager.createNativeQuery(sql);
            for (int i = 0; i < parameters.length; i++) {
                query.setParameter(i + 1, parameters[i]);
            }
            return ((Number) query.getSingleResult()).longValue();
        }

        private static LocalDateTime toLocalDateTime(Object value) {
            return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
        }

        private long count(String sql) {
            return ((Number) entityManager.createNativeQuery(sql)
                    .setParameter(1, userId)
                    .getSingleResult()).longValue();
        }
    }

    /**
     * place 조회부터 저장까지 실제 빈으로 거친다. 사용자 확인(user)만 대체한다. 테스트 트랜잭션은 롤백된다.
     * 마이그레이션의 장소와 섞이지 않게 테스트용 활성 지역 99001에 장소를 넣는다.
     */
    @Nested
    @DisplayName("개설 저장 결과")
    @DataJpaTest
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    @Import({CreateScheduleFacade.class, ActivityCategoryQueryService.class, ScheduleCommandService.class,
            ScheduleRepositoryImpl.class, ActivityCategoryRepositoryImpl.class, SchedulePlaceAdapter.class,
            PlaceQueryService.class, PlaceRepositoryImpl.class, JpaAuditingConfig.class})
    @EnableConfigurationProperties(ScheduleProperties.class)
    class SavedSchedule {

        private static final String REGION = "99001";

        @Autowired
        private CreateScheduleFacade createScheduleFacade;

        @Autowired
        private EntityManager entityManager;

        @MockitoBean
        private UserPort userPort;

        private final UUID userId = UUID.randomUUID();

        @BeforeEach
        void setUp() {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            when(userPort.isActive(userId)).thenReturn(true);
            entityManager.createNativeQuery("""
                            INSERT INTO regions (code, sido_name, sigungu_name, display_order, is_active, created_at, updated_at)
                            VALUES (?, '테스트시', '테스트구', 101, TRUE, NOW(), NOW())""")
                    .setParameter(1, REGION)
                    .executeUpdate();
        }

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("장소 id와 사본이 place의 노출 값과 같고, 개설자가 created_by에 남는다")
        void savesPlaceSnapshotAndCreator() {
            UUID placeId = insertPlace();

            Schedule schedule = createAndReload(placeId);

            assertThat(schedule.getPlaceId()).isEqualTo(placeId);
            assertThat(schedule.getPlace())
                    .extracting(PlaceSnapshot::getPlaceName, PlaceSnapshot::getPlaceRoadAddress,
                            PlaceSnapshot::getPlaceAddressDetail, PlaceSnapshot::getRegionCode)
                    .containsExactly("테스트보드게임카페", "서울특별시 테스트로 1", "3층", REGION);
            assertThat(schedule.getPlace().getPlaceLatitude()).isEqualByComparingTo("37.5512345");
            assertThat(schedule.getPlace().getPlaceLongitude()).isEqualByComparingTo("126.9212345");
            assertThat(schedule.getCreatedBy()).isEqualTo(userId.toString());
        }

        @Test
        @DisplayName("개설 후 장소 이름·주소가 고쳐지고 폐업 처리돼도 일정의 장소 사본은 그대로다")
        void snapshotIsKeptAfterPlaceChanges() {
            UUID placeId = insertPlace();
            UUID scheduleId = createAndReload(placeId).getId();

            entityManager.createNativeQuery("""
                            UPDATE places SET name = '바뀐이름', road_address = '서울특별시 바뀐로 9', is_closed = TRUE
                            WHERE id = ?""")
                    .setParameter(1, placeId)
                    .executeUpdate();
            entityManager.clear();

            assertThat(entityManager.find(Schedule.class, scheduleId).getPlace())
                    .extracting(PlaceSnapshot::getPlaceName, PlaceSnapshot::getPlaceRoadAddress)
                    .containsExactly("테스트보드게임카페", "서울특별시 테스트로 1");
        }

        private Schedule createAndReload(UUID placeId) {
            UUID scheduleId = createScheduleFacade.create(command(userId, placeId, futureStartAt()));
            entityManager.flush();
            entityManager.clear();
            return entityManager.find(Schedule.class, scheduleId);
        }

        private UUID insertPlace() {
            UUID id = UUID.randomUUID();
            entityManager.createNativeQuery("""
                            INSERT INTO places (id, place_type, name, road_address, address_detail, latitude, longitude,
                                                region_code, status, is_closed, source, source_id, source_name,
                                                source_road_address, source_address_detail, source_latitude,
                                                source_longitude, source_region_code, last_synced_at,
                                                created_at, updated_at, is_deleted)
                            VALUES (?, 'BOARD_GAME_CAFE', '테스트보드게임카페', '서울특별시 테스트로 1', '3층',
                                    37.5512345, 126.9212345, ?, 'APPROVED', FALSE, 'MANUAL', '', '', '', '', 0, 0, ?,
                                    NOW(), NOW(), NOW(), FALSE)""")
                    .setParameter(1, id)
                    .setParameter(2, REGION)
                    .setParameter(3, REGION)
                    .executeUpdate();
            return id;
        }
    }
}
