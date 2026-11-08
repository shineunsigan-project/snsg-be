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
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
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
                Duration.ofHours(12), Duration.ofHours(12));

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

    /**
     * 테스트 트랜잭션을 끄고 두 스레드가 각자 커밋한다. 커밋한 행은 끝나면 지운다.
     */
    @Nested
    @DisplayName("동시 개설")
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

        @AfterEach
        void deleteCommittedRows() {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
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
