package com.shineunsigan.snsg.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.shineunsigan.snsg.common.config.JpaAuditingConfig;
import com.shineunsigan.snsg.common.entity.Auditor;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.place.application.dto.command.CreateManualPlaceCommand;
import com.shineunsigan.snsg.place.application.dto.command.ReviewPlaceCommand;
import com.shineunsigan.snsg.place.application.dto.command.UpsertPlaceCommand;
import com.shineunsigan.snsg.place.application.dto.result.PlaceReviewResult;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.exception.PlaceErrorCode;
import com.shineunsigan.snsg.place.domain.model.Place;
import com.shineunsigan.snsg.place.domain.model.Region;
import com.shineunsigan.snsg.place.domain.repository.PlaceRepository;
import com.shineunsigan.snsg.place.domain.repository.RegionRepository;
import com.shineunsigan.snsg.place.infrastructure.persistence.PlaceRepositoryImpl;
import com.shineunsigan.snsg.place.infrastructure.persistence.RegionRepositoryImpl;

import jakarta.persistence.EntityManager;

class PlaceReviewCommandServiceTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final BigDecimal LAT = new BigDecimal("37.5512345");
    private static final BigDecimal LNG = new BigDecimal("126.9212345");

    private static ErrorCode errorCodeOf(Throwable throwable) {
        return ((BusinessException) throwable).getErrorCode();
    }

    private static ReviewPlaceCommand reviewCommand(UUID placeId, PlaceStatus status, String regionCode) {
        return new ReviewPlaceCommand(placeId, PlaceType.ESCAPE_ROOM, status, "검수이름", "서울특별시 마포구 검수로 1",
                "3층", LAT, LNG, regionCode, ADMIN_ID);
    }

    private static CreateManualPlaceCommand createCommand(String regionCode) {
        return new CreateManualPlaceCommand(PlaceType.BOARD_GAME_CAFE, "등록장소", "서울특별시 마포구 등록로 1", "",
                LAT, LNG, regionCode, ADMIN_ID);
    }

    @Nested
    @DisplayName("흐름")
    @ExtendWith(MockitoExtension.class)
    class Flow {

        private static final UUID PLACE_ID = UUID.randomUUID();

        @Mock
        private PlaceRepository placeRepository;

        @Mock
        private RegionRepository regionRepository;

        @InjectMocks
        private PlaceReviewCommandService placeReviewCommandService;

        private Place candidate() {
            return Place.createCandidate(PlaceSource.SEMAS, "TEST-1", PlaceType.UNCLASSIFIED, "원천이름",
                    "서울특별시 마포구 원천로 1", "2층", LAT, LNG, "11440", LocalDateTime.of(2026, 10, 27, 0, 0));
        }

        @Test
        @DisplayName("수정은 그 행을 잠가 읽고, 검수자를 그 어드민 id로 남겨 바로 반영한다")
        void reviewLocksAndRecordsReviewer() {
            Place place = candidate();
            when(placeRepository.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place));
            when(regionRepository.findByCode("11410")).thenReturn(Optional.of(mock(Region.class)));
            when(placeRepository.saveAndFlush(place)).thenReturn(place);

            PlaceReviewResult result = placeReviewCommandService.review(
                    reviewCommand(PLACE_ID, PlaceStatus.APPROVED, "11410"));

            assertThat(result.status()).isEqualTo(PlaceStatus.APPROVED);
            assertThat(result.reviewedBy()).isEqualTo(ADMIN_ID.toString());
            assertThat(result.reviewedAt()).isNotNull();
            verify(placeRepository, never()).findById(any());
        }

        @Test
        @DisplayName("없는 장소면 PLACE_NOT_FOUND이고 지역을 보지 않는다")
        void reviewNotFound() {
            when(placeRepository.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> placeReviewCommandService.review(
                    reviewCommand(PLACE_ID, PlaceStatus.APPROVED, "11410")))
                    .extracting(PlaceReviewCommandServiceTest::errorCodeOf)
                    .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
            verify(regionRepository, never()).findByCode(any());
        }

        @Test
        @DisplayName("수정의 지역이 regions에 없으면 INVALID_REGION이고 저장하지 않는다. 검수 상태보다 먼저 본다")
        void reviewUnknownRegion() {
            Place place = candidate();
            when(placeRepository.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place));
            when(regionRepository.findByCode("99999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> placeReviewCommandService.review(
                    reviewCommand(PLACE_ID, PlaceStatus.CANDIDATE, "99999")))
                    .extracting(PlaceReviewCommandServiceTest::errorCodeOf)
                    .isEqualTo(PlaceErrorCode.INVALID_REGION);
            assertThat(place.getStatus()).isEqualTo(PlaceStatus.CANDIDATE);
            verify(placeRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("등록의 지역이 regions에 없으면 INVALID_REGION이고 저장하지 않는다")
        void createUnknownRegion() {
            when(regionRepository.findByCode("99999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> placeReviewCommandService.createManual(createCommand("99999")))
                    .extracting(PlaceReviewCommandServiceTest::errorCodeOf)
                    .isEqualTo(PlaceErrorCode.INVALID_REGION);
            verify(placeRepository, never()).save(any());
        }

        @Test
        @DisplayName("등록은 MANUAL·승인 장소를 그 어드민 id를 검수자로 저장한다")
        void createManualSavesApprovedManual() {
            when(regionRepository.findByCode("11440")).thenReturn(Optional.of(mock(Region.class)));
            when(placeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            placeReviewCommandService.createManual(createCommand("11440"));

            ArgumentCaptor<Place> saved = ArgumentCaptor.forClass(Place.class);
            verify(placeRepository).save(saved.capture());
            assertThat(saved.getValue()).extracting(Place::getSource, Place::getStatus, Place::getReviewedBy)
                    .containsExactly(PlaceSource.MANUAL, PlaceStatus.APPROVED, ADMIN_ID.toString());
        }
    }

    /**
     * 실제 PostgreSQL에서 커밋까지 거친다. 테스트 트랜잭션을 꺼서 서비스 호출이 각자 커밋한다.
     * 공유 DB라 무작위 원천 식별자로 넣고, 끝나면 넣은 id로만 지운다. 화면 지역은 regions에 있어야 수정·등록이 되므로
     * 마이그레이션의 11440을 쓴다. 원천 지역은 폐업 처리 비율이 다른 행과 섞이지 않게 무작위 코드를 쓴다.
     */
    @Nested
    @DisplayName("커밋된 수정·등록과 적재")
    @DataJpaTest
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    @Import({PlaceReviewCommandService.class, PlaceReviewQueryService.class, PlaceCommandService.class,
            PlaceRepositoryImpl.class, RegionRepositoryImpl.class, JpaAuditingConfig.class,
            PlaceReviewCommandServiceTest.StoredConfig.class})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class Stored {

        private static final String REGION = "11440";
        private static final LocalDateTime OLD_SYNC = LocalDateTime.of(2026, 10, 27, 0, 0);

        @Autowired
        private PlaceReviewCommandService placeReviewCommandService;

        @Autowired
        private PlaceReviewQueryService placeReviewQueryService;

        @Autowired
        private PlaceCommandService placeCommandService;

        @Autowired
        private EntityManager entityManager;

        @Autowired
        private PlatformTransactionManager transactionManager;

        private final List<UUID> insertedIds = new ArrayList<>();

        @AfterEach
        void deleteInsertedRows() {
            SecurityContextHolder.clearContext();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> insertedIds.forEach(id ->
                    entityManager.createNativeQuery("DELETE FROM places WHERE id = ?")
                            .setParameter(1, id)
                            .executeUpdate()));
        }

        // 감사 값

        @Test
        @DisplayName("어드민이 수정하면 updated_by·reviewed_by가 그 adminId이고, 뒤이은 적재 확인은 updated_by만 스케줄러로 덮는다")
        void reviewerSurvivesConfirmSource() {
            String sourceId = randomSourceId();
            UUID id = insertSemas(sourceId, PlaceStatus.CANDIDATE, false, OLD_SYNC, REGION);

            asAdmin(() -> placeReviewCommandService.review(reviewCommand(id, PlaceStatus.APPROVED, REGION)));
            Object[] reviewed = selectAudit(id);
            runAsScheduler(() -> placeCommandService.upsert(List.of(newSource(sourceId))));
            Object[] confirmed = selectAudit(id);

            assertThat(reviewed[0]).isEqualTo(Auditor.SYSTEM);
            assertThat(reviewed[1]).isEqualTo(ADMIN_ID.toString());
            assertThat(reviewed[2]).isEqualTo(ADMIN_ID.toString());
            assertThat(reviewed[3]).isNotNull();
            assertThat(confirmed).containsExactly(Auditor.SYSTEM, Auditor.SYSTEM_SCHEDULER, ADMIN_ID.toString(),
                    reviewed[3]);
            assertThat(selectColumn(id, "source_name")).isEqualTo("새원천이름");
        }

        @Test
        @DisplayName("확인 시각만 갱신하는 일괄 UPDATE는 updated_by를 SYSTEM_SCHEDULER로 넣고 reviewed_by·reviewed_at은 그대로 둔다")
        void reviewerSurvivesMarkSynced() {
            String sourceId = randomSourceId();
            UUID id = insertSemas(sourceId, PlaceStatus.CANDIDATE, false, OLD_SYNC, REGION);
            asAdmin(() -> placeReviewCommandService.review(reviewCommand(id, PlaceStatus.APPROVED, REGION)));
            Object[] reviewed = selectAudit(id);

            placeCommandService.reflectPage(PlaceSource.SEMAS, List.of(), List.of(sourceId));

            assertThat(selectAudit(id)).containsExactly(Auditor.SYSTEM, Auditor.SYSTEM_SCHEDULER,
                    ADMIN_ID.toString(), reviewed[3]);
            assertThat(toLocalDateTime(selectColumn(id, "last_synced_at"))).isAfter(OLD_SYNC);
        }

        @Test
        @DisplayName("적재가 처음 만든 후보는 created_by·updated_by가 SYSTEM_SCHEDULER이고 검수 기록이 없다")
        void newCandidateHasNoReviewer() {
            String sourceId = randomSourceId();

            runAsScheduler(() -> placeCommandService.upsert(List.of(newSource(sourceId))));
            UUID id = (UUID) entityManager.createNativeQuery("SELECT id FROM places WHERE source_id = ?")
                    .setParameter(1, sourceId)
                    .getSingleResult();
            insertedIds.add(id);

            assertThat(selectAudit(id)).containsExactly(Auditor.SYSTEM_SCHEDULER, Auditor.SYSTEM_SCHEDULER, "", null);
        }

        @Test
        @DisplayName("MANUAL 등록은 created_by·updated_by·reviewed_by가 그 adminId이고, 다시 읽은 원천 지역은 ''·원천 좌표는 0이다")
        void createManualRecordsAdmin() {
            PlaceReviewResult created = asAdmin(() -> placeReviewCommandService.createManual(createCommand(REGION)));
            insertedIds.add(created.id());

            Object[] audit = selectAudit(created.id());
            assertThat(Arrays.copyOf(audit, 3)).containsExactly(ADMIN_ID.toString(), ADMIN_ID.toString(),
                    ADMIN_ID.toString());
            assertThat(toLocalDateTime(audit[3])).isEqualTo(toLocalDateTime(selectColumn(created.id(), "last_synced_at")));
            PlaceReviewResult stored = placeReviewQueryService.get(created.id());
            assertThat(stored).extracting(PlaceReviewResult::source, PlaceReviewResult::status,
                            PlaceReviewResult::closed, PlaceReviewResult::sourceId, PlaceReviewResult::sourceRegionCode)
                    .containsExactly(PlaceSource.MANUAL, PlaceStatus.APPROVED, false, "", "");
            assertThat(stored.sourceLatitude().toString()).isEqualTo("0");
            assertThat(stored.sourceLongitude().toString()).isEqualTo("0");
        }

        // 적재와 어드민 수정의 동시 실행

        @Test
        @DisplayName("적재 반영(upsert)과 어드민 수정이 같은 행에서 동시에 커밋돼도, 행에 어드민의 검수 결과와 적재의 원천 값·폐업 해제가 함께 남는다")
        void concurrentUpsertAndReviewKeepBoth() throws Exception {
            String sourceId = randomSourceId();
            UUID id = insertSemas(sourceId, PlaceStatus.CANDIDATE, true, OLD_SYNC, REGION);

            runConcurrently(
                    asScheduler(() -> {
                        placeCommandService.upsert(List.of(newSource(sourceId)));
                        return null;
                    }),
                    asAdminTask(() -> placeReviewCommandService.review(reviewCommand(id, PlaceStatus.APPROVED, REGION))));

            assertThat(selectRow(id)).containsExactly(PlaceStatus.APPROVED.name(), "검수이름", "서울특별시 마포구 검수로 1",
                    "새원천이름", "서울특별시 새원천로 9", false, ADMIN_ID.toString());
            assertThat(toLocalDateTime(selectColumn(id, "last_synced_at"))).isAfter(OLD_SYNC);
        }

        @Test
        @DisplayName("폐업 처리(closeMissing)와 어드민 수정이 같은 행에서 동시에 커밋돼도, 행에 어드민의 검수 결과와 폐업이 함께 남는다")
        void concurrentCloseAndReviewKeepBoth() throws Exception {
            String sourceRegion = randomSourceRegion();
            UUID id = insertSemas(randomSourceId(), PlaceStatus.APPROVED, false, OLD_SYNC, sourceRegion);
            LocalDateTime jobStartedAt = OLD_SYNC.plusDays(1);
            // 폐업 비율 안전장치(0.3)에 걸리지 않도록 같은 원천 지역에 다시 확인된 열린 행을 넷 둔다.
            for (int i = 0; i < 4; i++) {
                insertSemas(randomSourceId(), PlaceStatus.APPROVED, false, jobStartedAt.plusHours(1), sourceRegion);
            }

            runConcurrently(
                    asScheduler(() -> {
                        placeCommandService.closeMissing(PlaceSource.SEMAS, List.of(sourceRegion), jobStartedAt);
                        return null;
                    }),
                    asAdminTask(() -> placeReviewCommandService.review(reviewCommand(id, PlaceStatus.APPROVED, REGION))));

            assertThat(selectRow(id)).containsExactly(PlaceStatus.APPROVED.name(), "검수이름", "서울특별시 마포구 검수로 1",
                    "원천이름", "서울특별시 원천로 1", true, ADMIN_ID.toString());
        }

        /** 두 작업을 준비·출발 래치로 맞춰 동시에 부르고, 둘 다 예외 없이 끝나는지 본다. */
        private void runConcurrently(Callable<?> first, Callable<?> second) throws Exception {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                List<Future<?>> futures = List.of(executor.submit(latched(ready, start, first)),
                        executor.submit(latched(ready, start, second)));
                ready.await();
                start.countDown();
                for (Future<?> future : futures) {
                    future.get(30, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }
        }

        private Callable<Object> latched(CountDownLatch ready, CountDownLatch start, Callable<?> task) {
            return () -> {
                ready.countDown();
                start.await();
                return task.call();
            };
        }

        /** SecurityContext는 스레드마다 따로라, 작업을 부르는 스레드 안에서 principal을 넣고 끝나면 비운다. */
        private Callable<Object> withPrincipal(Object principal, Callable<?> task) {
            return () -> {
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
                try {
                    return task.call();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
        }

        private Callable<Object> asScheduler(Callable<?> task) {
            return withPrincipal(Auditor.SYSTEM_SCHEDULER, task);
        }

        private Callable<Object> asAdminTask(Callable<?> task) {
            return withPrincipal(ADMIN_ID, task);
        }

        private <T> T asAdmin(Callable<T> task) {
            return call(withPrincipal(ADMIN_ID, task));
        }

        private void runAsScheduler(Runnable task) {
            call(withPrincipal(Auditor.SYSTEM_SCHEDULER, () -> {
                task.run();
                return null;
            }));
        }

        @SuppressWarnings("unchecked")
        private <T> T call(Callable<?> task) {
            try {
                return (T) task.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        private UpsertPlaceCommand newSource(String sourceId) {
            return new UpsertPlaceCommand(PlaceSource.SEMAS, sourceId, "새원천이름", "서울특별시 새원천로 9", "5층",
                    new BigDecimal("37.5"), new BigDecimal("126.9"), REGION, PlaceType.ESCAPE_ROOM);
        }

        /** 원천 값이 '원천이름'·'서울특별시 원천로 1'인 SEMAS 행을 커밋한다. 만든 주체는 SYSTEM이고 검수 기록이 없다. */
        private UUID insertSemas(String sourceId, PlaceStatus status, boolean closed, LocalDateTime syncedAt,
                String sourceRegion) {
            UUID id = UUID.randomUUID();
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> entityManager
                    .createNativeQuery("""
                            INSERT INTO places (id, place_type, name, road_address, address_detail, latitude, longitude,
                                                region_code, status, is_closed, source, source_id, source_name,
                                                source_road_address, source_address_detail, source_latitude,
                                                source_longitude, source_region_code, last_synced_at,
                                                created_at, updated_at, is_deleted, created_by, updated_by, reviewed_by)
                            VALUES (?, 'ESCAPE_ROOM', '원래이름', '서울특별시 원래로 1', '', 37.5, 126.9, ?, ?, ?,
                                    'SEMAS', ?, '원천이름', '서울특별시 원천로 1', '2층', 37.5, 126.9, ?, ?,
                                    NOW(), NOW(), FALSE, 'SYSTEM', 'SYSTEM', '')""")
                    .setParameter(1, id)
                    .setParameter(2, REGION)
                    .setParameter(3, status.name())
                    .setParameter(4, closed)
                    .setParameter(5, sourceId)
                    .setParameter(6, sourceRegion)
                    .setParameter(7, syncedAt)
                    .executeUpdate());
            insertedIds.add(id);
            return id;
        }

        /** created_by, updated_by, reviewed_by, reviewed_at. */
        private Object[] selectAudit(UUID id) {
            Object[] row = (Object[]) entityManager.createNativeQuery(
                            "SELECT created_by, updated_by, reviewed_by, reviewed_at FROM places WHERE id = ?")
                    .setParameter(1, id)
                    .getSingleResult();
            row[3] = row[3] == null ? null : toLocalDateTime(row[3]);
            return row;
        }

        /** status, name, road_address, source_name, source_road_address, is_closed, reviewed_by. */
        private Object[] selectRow(UUID id) {
            return (Object[]) entityManager.createNativeQuery("""
                            SELECT status, name, road_address, source_name, source_road_address, is_closed, reviewed_by
                            FROM places WHERE id = ?""")
                    .setParameter(1, id)
                    .getSingleResult();
        }

        private Object selectColumn(UUID id, String column) {
            return entityManager.createNativeQuery("SELECT " + column + " FROM places WHERE id = ?")
                    .setParameter(1, id)
                    .getSingleResult();
        }

        private LocalDateTime toLocalDateTime(Object value) {
            return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
        }

        private String randomSourceId() {
            return "TEST-" + UUID.randomUUID();
        }

        /** 실제 시군구 코드와 겹치지 않는 97000~97999. */
        private String randomSourceRegion() {
            return "97" + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        }
    }

    @TestConfiguration
    static class StoredConfig {

        @Bean
        PlaceProperties placeProperties() {
            return new PlaceProperties(new BigDecimal("0.3"), Duration.ofSeconds(10), 3, List.of(), List.of(),
                    Map.of(), 20, 50);
        }
    }
}
