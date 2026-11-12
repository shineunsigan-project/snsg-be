package com.shineunsigan.snsg.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.admin.domain.model.Admin;
import com.shineunsigan.snsg.admin.domain.repository.AdminRepository;
import com.shineunsigan.snsg.admin.infrastructure.persistence.AdminRepositoryImpl;
import com.shineunsigan.snsg.common.config.JpaAuditingConfig;
import com.shineunsigan.snsg.common.entity.Auditor;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.ErrorCode;

import jakarta.persistence.EntityManager;

class AdminCommandServiceTest {

    /** 동작 이름과 그 동작을 부르는 방법. */
    static Stream<Arguments> actions() {
        return Stream.of(
                Arguments.of("승인", (BiConsumer<AdminCommandService, UUID>) AdminCommandService::approve),
                Arguments.of("거절", (BiConsumer<AdminCommandService, UUID>) AdminCommandService::reject),
                Arguments.of("이용 중지", (BiConsumer<AdminCommandService, UUID>) AdminCommandService::suspend),
                Arguments.of("해제", (BiConsumer<AdminCommandService, UUID>) AdminCommandService::unsuspend));
    }

    private static ErrorCode errorCodeOf(Throwable throwable) {
        return ((BusinessException) throwable).getErrorCode();
    }

    @Nested
    @DisplayName("상태 변경 흐름")
    @ExtendWith(MockitoExtension.class)
    class Flow {

        private static final UUID ADMIN_ID = UUID.randomUUID();

        @Mock
        private AdminRepository adminRepository;

        @InjectMocks
        private AdminCommandService adminCommandService;

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.shineunsigan.snsg.admin.application.AdminCommandServiceTest#actions")
        @DisplayName("대상을 잠금 조회로 읽고, 잠금 없는 조회는 부르지 않는다")
        void readsTargetWithLock(String name, BiConsumer<AdminCommandService, UUID> action) {
            Admin admin = Admin.signUp("mintadmin", "stored-hash", "김민트", AdminTeam.BACKEND,
                    LocalDate.of(1996, 4, 12), LocalDate.of(2026, 11, 13));
            when(adminRepository.findByIdForUpdate(ADMIN_ID)).thenReturn(Optional.of(admin));

            try {
                action.accept(adminCommandService, ADMIN_ID);
            } catch (BusinessException exception) {
                // 출발 상태가 맞지 않는 동작(중지·해제)은 409다. 여기서는 어떤 조회를 썼는지만 본다.
            }

            verify(adminRepository).findByIdForUpdate(ADMIN_ID);
            verify(adminRepository, never()).findById(any());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.shineunsigan.snsg.admin.application.AdminCommandServiceTest#actions")
        @DisplayName("대상이 없거나 삭제됐으면 ADMIN_NOT_FOUND")
        void notFound(String name, BiConsumer<AdminCommandService, UUID> action) {
            when(adminRepository.findByIdForUpdate(ADMIN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> action.accept(adminCommandService, ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(AdminCommandServiceTest::errorCodeOf)
                    .isEqualTo(AdminErrorCode.ADMIN_NOT_FOUND);
        }
    }

    /**
     * 실제 PostgreSQL에서 커밋까지 거친다. 테스트 트랜잭션을 꺼서 서비스 호출이 각자 커밋한다.
     * 공유 DB라 무작위 아이디로 넣고, 끝나면 넣은 id로만 지운다.
     */
    @Nested
    @DisplayName("커밋된 상태 변경")
    @DataJpaTest
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    @Import({AdminCommandService.class, AdminRepositoryImpl.class, JpaAuditingConfig.class})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class Stored {

        private static final UUID MASTER_ID = UUID.randomUUID();

        @Autowired
        private AdminCommandService adminCommandService;

        @Autowired
        private AdminRepository adminRepository;

        @Autowired
        private EntityManager entityManager;

        @Autowired
        private PlatformTransactionManager transactionManager;

        private final List<UUID> insertedIds = new ArrayList<>();

        @AfterEach
        void deleteInsertedRows() {
            SecurityContextHolder.clearContext();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> insertedIds.forEach(id ->
                    entityManager.createNativeQuery("DELETE FROM admins WHERE id = ?")
                            .setParameter(1, id)
                            .executeUpdate()));
        }

        @Test
        @DisplayName("같은 승인 대기 어드민에 승인과 거절이 동시에 오면 하나만 성공하고 다른 하나는 ADMIN_STATUS_CONFLICT다")
        void onlyOneOfConcurrentApproveAndRejectSucceeds() throws Exception {
            UUID adminId = insertPendingAdmin();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<AdminStatus> approve = asMaster(ready, start, () -> {
                adminCommandService.approve(adminId);
                return AdminStatus.APPROVED;
            });
            Callable<AdminStatus> reject = asMaster(ready, start, () -> {
                adminCommandService.reject(adminId);
                return AdminStatus.REJECTED;
            });

            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<AdminStatus> succeeded = new ArrayList<>();
            List<Throwable> failures = new ArrayList<>();
            try {
                List<Future<AdminStatus>> futures = List.of(executor.submit(approve), executor.submit(reject));
                ready.await();
                start.countDown();
                for (Future<AdminStatus> future : futures) {
                    try {
                        succeeded.add(future.get(30, TimeUnit.SECONDS));
                    } catch (ExecutionException exception) {
                        failures.add(exception.getCause());
                    }
                }
            } finally {
                executor.shutdownNow();
            }

            assertThat(succeeded).hasSize(1);
            assertThat(failures).singleElement()
                    .isInstanceOf(BusinessException.class)
                    .extracting(AdminCommandServiceTest::errorCodeOf)
                    .isEqualTo(AdminErrorCode.ADMIN_STATUS_CONFLICT);
            assertThat(selectColumn(adminId, "status")).isEqualTo(succeeded.get(0).name());
        }

        @Test
        @DisplayName("MASTER가 승인하면 updated_by는 그 MASTER의 adminId이고 created_by는 그대로다")
        void approveRecordsMasterAsUpdatedBy() {
            UUID adminId = insertPendingAdmin();
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(MASTER_ID, null, List.of()));

            adminCommandService.approve(adminId);

            assertThat(selectColumn(adminId, "status")).isEqualTo(AdminStatus.APPROVED.name());
            assertThat(selectColumn(adminId, "updated_by")).isEqualTo(MASTER_ID.toString());
            assertThat(selectColumn(adminId, "created_by")).isEqualTo(Auditor.SYSTEM);
        }

        /** 인증 없이 저장해 created_by는 SYSTEM이다. */
        private UUID insertPendingAdmin() {
            UUID id = new TransactionTemplate(transactionManager).execute(status -> adminRepository.save(
                    Admin.signUp(randomLoginId(), "stored-hash", "김민트", AdminTeam.BACKEND,
                            LocalDate.of(1996, 4, 12), LocalDate.now())).getId());
            insertedIds.add(id);
            return id;
        }

        /** SecurityContext는 스레드마다 따로라 스레드 안에서 MASTER principal을 넣고 끝나면 비운다. */
        private <T> Callable<T> asMaster(CountDownLatch ready, CountDownLatch start, Callable<T> task) {
            return () -> {
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(MASTER_ID, null, List.of()));
                try {
                    ready.countDown();
                    start.await();
                    return task.call();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
        }

        private String selectColumn(UUID adminId, String column) {
            return (String) entityManager.createNativeQuery("SELECT " + column + " FROM admins WHERE id = ?")
                    .setParameter(1, adminId)
                    .getSingleResult();
        }

        /** 영문 소문자로 시작하는 20자. 다른 테스트·워크트리의 행과 겹치지 않게 한다. */
        private String randomLoginId() {
            return "a" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
        }
    }
}
