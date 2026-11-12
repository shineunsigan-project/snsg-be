package com.shineunsigan.snsg.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.admin.application.dto.result.AdminPageResult;
import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.admin.domain.model.Admin;
import com.shineunsigan.snsg.admin.domain.model.AdminCursor;
import com.shineunsigan.snsg.admin.domain.repository.AdminRepository;
import com.shineunsigan.snsg.common.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class AdminQueryServiceTest {

    /** 운영 값(20·50)과 다르게 넣어 설정값을 읽는지 본다. */
    private static final int DEFAULT_SIZE = 3;
    private static final int MAX_SIZE = 5;

    @Mock
    private AdminRepository adminRepository;

    private AdminQueryService adminQueryService;

    @BeforeEach
    void setUp() {
        adminQueryService = new AdminQueryService(adminRepository, new AdminProperties(Duration.ofMinutes(30),
                Duration.ofHours(12), 5, Duration.ofMinutes(15), DEFAULT_SIZE, MAX_SIZE));
    }

    @Nested
    @DisplayName("목록")
    class ListPage {

        @ParameterizedTest(name = "size={0} → {1}건 + 1")
        @CsvSource(nullValues = "null", value = {"null, 3", "2, 2", "5, 5", "6, 5", "100, 5", "0, 1", "-1, 1"})
        @DisplayName("size는 생략하면 기본값, 상한을 넘으면 상한, 1보다 작으면 1이고 한 건 더 읽는다")
        void pageSize(Integer requested, int expected) {
            when(adminRepository.findAll(any(), any(), anyInt())).thenReturn(List.of());

            adminQueryService.list(null, null, requested);

            verify(adminRepository).findAll(null, null, expected + 1);
        }

        @Test
        @DisplayName("상태와 해석한 커서를 그대로 넘긴다. 빈 커서는 처음부터다")
        void passesStatusAndCursor() {
            AdminCursor cursor = new AdminCursor(LocalDateTime.of(2026, 11, 13, 10, 0), UUID.randomUUID());
            when(adminRepository.findAll(any(), any(), anyInt())).thenReturn(List.of());

            adminQueryService.list(AdminStatus.PENDING, cursor.encode(), null);
            adminQueryService.list(AdminStatus.SUSPENDED, "", null);

            verify(adminRepository).findAll(AdminStatus.PENDING, cursor, DEFAULT_SIZE + 1);
            verify(adminRepository).findAll(eq(AdminStatus.SUSPENDED), isNull(), eq(DEFAULT_SIZE + 1));
        }

        @Test
        @DisplayName("해석할 수 없는 커서면 INVALID_CURSOR이고 조회하지 않는다")
        void invalidCursor() {
            assertThatThrownBy(() -> adminQueryService.list(null, "not base64!", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(AdminErrorCode.INVALID_CURSOR);
            verifyNoInteractions(adminRepository);
        }

        @Test
        @DisplayName("size + 1건이 오면 size건만 주고 마지막 항목의 (가입 신청 시각, id)를 다음 커서로 준다")
        void hasNext() {
            List<Admin> admins = IntStream.range(0, DEFAULT_SIZE + 1).mapToObj(i -> admin(i)).toList();
            when(adminRepository.findAll(null, null, DEFAULT_SIZE + 1)).thenReturn(admins);

            AdminPageResult page = adminQueryService.list(null, null, null);

            Admin last = admins.get(DEFAULT_SIZE - 1);
            assertThat(page.items()).hasSize(DEFAULT_SIZE);
            assertThat(AdminCursor.decode(page.nextCursor()))
                    .isEqualTo(new AdminCursor(last.getCreatedAt(), last.getId()));
        }

        @Test
        @DisplayName("size건 이하가 오면 마지막 페이지라 nextCursor는 null이다")
        void lastPage() {
            List<Admin> admins = IntStream.range(0, DEFAULT_SIZE).mapToObj(i -> admin(i)).toList();
            when(adminRepository.findAll(null, null, DEFAULT_SIZE + 1)).thenReturn(admins);

            AdminPageResult page = adminQueryService.list(null, null, null);

            assertThat(page.items()).hasSize(DEFAULT_SIZE);
            assertThat(page.nextCursor()).isNull();
        }

        private Admin admin(int index) {
            Admin admin = mock(Admin.class);
            // 다음 페이지 유무를 정하려고 더 읽은 한 건은 결과로 바꾸지 않아 스텁이 쓰이지 않는다.
            lenient().when(admin.getId()).thenReturn(UUID.randomUUID());
            lenient().when(admin.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 13, 10, index));
            return admin;
        }
    }

    @ParameterizedTest
    @EnumSource(value = AdminStatus.class, names = {"REJECTED", "SUSPENDED"})
    @DisplayName("거절·이용 중지 어드민은 승인되지 않은 것으로 본다(인증 필터·재발급이 막힌다)")
    void rejectedOrSuspendedIsNotApproved(AdminStatus status) {
        UUID adminId = UUID.randomUUID();
        Admin admin = Admin.signUp("mintadmin", "stored-hash", "김민트", AdminTeam.BACKEND,
                LocalDate.of(1996, 4, 12), LocalDate.of(2026, 11, 13));
        if (status == AdminStatus.REJECTED) {
            admin.reject();
        } else {
            admin.approve();
            admin.suspend();
        }
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));

        assertThat(adminQueryService.isApproved(adminId)).isFalse();
        assertThat(adminQueryService.findApprovedRole(adminId)).isEmpty();
    }
}
