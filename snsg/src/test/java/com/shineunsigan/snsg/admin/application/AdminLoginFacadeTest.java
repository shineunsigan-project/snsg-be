package com.shineunsigan.snsg.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.admin.application.dto.command.AdminLoginCommand;
import com.shineunsigan.snsg.admin.application.dto.result.AdminCredentialResult;
import com.shineunsigan.snsg.admin.application.dto.result.AdminTokenResult;
import com.shineunsigan.snsg.admin.application.port.out.AdminRefreshTokenStorePort;
import com.shineunsigan.snsg.admin.application.port.out.AdminTokenPort;
import com.shineunsigan.snsg.admin.application.port.out.LoginAttemptPort;
import com.shineunsigan.snsg.admin.application.port.out.PasswordHashPort;
import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AdminLoginFacadeTest {

    private static final String LOGIN_ID = "mintadmin";
    private static final String PASSWORD = "mint1234!";
    private static final String HASH = "stored-hash";
    private static final UUID ADMIN_ID = UUID.randomUUID();
    /** 운영 값(5회·15분)과 다르게 넣어 설정값을 읽는지 본다. */
    private static final int MAX_FAILURES = 3;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(7);
    private static final Duration SESSION_VALIDITY = Duration.ofHours(12);

    @Mock
    private LoginAttemptPort loginAttemptPort;

    @Mock
    private PasswordHashPort passwordHashPort;

    @Mock
    private AdminQueryService adminQueryService;

    @Mock
    private AdminTokenPort adminTokenPort;

    @Mock
    private AdminRefreshTokenStorePort adminRefreshTokenStorePort;

    private AdminLoginFacade facade;

    @BeforeEach
    void setUp() {
        AdminProperties properties = new AdminProperties(Duration.ofMinutes(30), SESSION_VALIDITY, MAX_FAILURES,
                LOCK_DURATION, 20, 50);
        facade = new AdminLoginFacade(loginAttemptPort, passwordHashPort, adminQueryService,
                new AdminTokenIssuer(adminTokenPort, adminRefreshTokenStorePort, properties), properties);
    }

    @Test
    @DisplayName("맞는 아이디·비밀번호이고 승인됐으면 새 세션(로그인 시각 + refresh 수명에 만료)으로 발급하고 실패 횟수를 초기화한다")
    void login() {
        givenAdmin(AdminStatus.APPROVED);
        when(passwordHashPort.matches(PASSWORD, HASH)).thenReturn(true);
        when(adminTokenPort.issueAccessToken(eq(ADMIN_ID), any())).thenReturn("access");
        when(adminTokenPort.issueRefreshToken(eq(ADMIN_ID), anyString(), any())).thenReturn("refresh");
        Instant before = Instant.now();

        AdminTokenResult result = facade.login(new AdminLoginCommand(LOGIN_ID, PASSWORD));

        Instant sessionExpiresAt = result.refreshExpiresAt();
        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(sessionExpiresAt).isBetween(before.plus(SESSION_VALIDITY).minusSeconds(1),
                Instant.now().plus(SESSION_VALIDITY));
        verify(adminTokenPort).issueRefreshToken(eq(ADMIN_ID), anyString(), eq(sessionExpiresAt));
        verify(adminRefreshTokenStorePort).save(eq(ADMIN_ID), anyString(), eq("refresh"), eq(sessionExpiresAt));
        verify(loginAttemptPort).reset(LOGIN_ID);
        verify(loginAttemptPort, never()).recordFailure(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("아이디가 없으면 INVALID_ADMIN_CREDENTIALS. 더미 해시 대조를 한 번 하고 실패를 기록한다")
    void unknownLoginId() {
        when(adminQueryService.findCredential(LOGIN_ID)).thenReturn(Optional.empty());

        assertError(() -> facade.login(new AdminLoginCommand(LOGIN_ID, PASSWORD)),
                AdminErrorCode.INVALID_ADMIN_CREDENTIALS);

        verify(passwordHashPort, times(1)).matches(eq(PASSWORD), isNull());
        verify(loginAttemptPort).recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION);
        verifyNoInteractions(adminTokenPort, adminRefreshTokenStorePort);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 INVALID_ADMIN_CREDENTIALS이고 실패를 기록한다")
    void wrongPassword() {
        givenAdmin(AdminStatus.APPROVED);

        assertError(() -> facade.login(new AdminLoginCommand(LOGIN_ID, "wrong1234")),
                AdminErrorCode.INVALID_ADMIN_CREDENTIALS);

        verify(loginAttemptPort).recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION);
        verify(loginAttemptPort, never()).reset(anyString());
        verifyNoInteractions(adminTokenPort, adminRefreshTokenStorePort);
    }

    @Test
    @DisplayName("비밀번호가 맞고 승인 대기면 ADMIN_PENDING_APPROVAL. 토큰을 발급하지 않고 실패 횟수는 초기화한다")
    void pendingApproval() {
        givenAdmin(AdminStatus.PENDING);
        when(passwordHashPort.matches(PASSWORD, HASH)).thenReturn(true);

        assertError(() -> facade.login(new AdminLoginCommand(LOGIN_ID, PASSWORD)),
                AdminErrorCode.ADMIN_PENDING_APPROVAL);

        verify(loginAttemptPort).reset(LOGIN_ID);
        verifyNoInteractions(adminTokenPort, adminRefreshTokenStorePort);
    }

    @ParameterizedTest
    @CsvSource({"REJECTED, ADMIN_REJECTED", "SUSPENDED, ADMIN_SUSPENDED", "PENDING, ADMIN_PENDING_APPROVAL"})
    @DisplayName("비밀번호가 맞고 거절·이용 중지·승인 대기면 상태별 403 코드. 토큰을 발급하지 않고 실패 횟수는 초기화한다")
    void notApprovedStatus(AdminStatus status, AdminErrorCode expected) {
        givenAdmin(status);
        when(passwordHashPort.matches(PASSWORD, HASH)).thenReturn(true);

        assertError(() -> facade.login(new AdminLoginCommand(LOGIN_ID, PASSWORD)), expected);

        verify(loginAttemptPort).reset(LOGIN_ID);
        verify(loginAttemptPort, never()).recordFailure(anyString(), anyInt(), any());
        verifyNoInteractions(adminTokenPort, adminRefreshTokenStorePort);
    }

    @ParameterizedTest
    @EnumSource(value = AdminStatus.class, names = {"PENDING", "REJECTED", "SUSPENDED"})
    @DisplayName("비밀번호가 틀리면 상태와 관계없이 INVALID_ADMIN_CREDENTIALS이고 실패를 기록한다(상태가 드러나지 않는다)")
    void wrongPasswordHidesStatus(AdminStatus status) {
        givenAdmin(status);

        assertError(() -> facade.login(new AdminLoginCommand(LOGIN_ID, "wrong1234")),
                AdminErrorCode.INVALID_ADMIN_CREDENTIALS);

        verify(loginAttemptPort).recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION);
        verify(loginAttemptPort, never()).reset(anyString());
        verifyNoInteractions(adminTokenPort, adminRefreshTokenStorePort);
    }

    @Test
    @DisplayName("이번 실패로 설정 횟수에 이르면 ADMIN_LOGIN_LOCKED")
    void lockedOnLastFailure() {
        givenAdmin(AdminStatus.APPROVED);
        when(loginAttemptPort.recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION)).thenReturn(true);

        assertError(() -> facade.login(new AdminLoginCommand(LOGIN_ID, "wrong1234")),
                AdminErrorCode.ADMIN_LOGIN_LOCKED);
    }

    @Test
    @DisplayName("잠긴 동안은 비밀번호가 맞아도 ADMIN_LOGIN_LOCKED. 조회·대조·실패 기록을 하지 않는다")
    void lockedRejectsEvenCorrectPassword() {
        when(loginAttemptPort.isLocked(LOGIN_ID)).thenReturn(true);

        assertError(() -> facade.login(new AdminLoginCommand(LOGIN_ID, PASSWORD)),
                AdminErrorCode.ADMIN_LOGIN_LOCKED);

        verifyNoInteractions(adminQueryService, passwordHashPort, adminTokenPort, adminRefreshTokenStorePort);
        verify(loginAttemptPort, never()).recordFailure(anyString(), anyInt(), any());
        verify(loginAttemptPort, never()).reset(anyString());
    }

    @Test
    @DisplayName("UTF-8 72바이트를 넘는 비밀번호(한글 64자)는 대조하지 않고 INVALID_ADMIN_CREDENTIALS. 예외가 새지 않는다")
    void passwordOverHashLimit() {
        givenAdmin(AdminStatus.APPROVED);

        assertError(() -> facade.login(new AdminLoginCommand(LOGIN_ID, "가".repeat(64))),
                AdminErrorCode.INVALID_ADMIN_CREDENTIALS);

        verify(passwordHashPort, never()).matches(anyString(), any());
        verify(loginAttemptPort).recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION);
    }

    private void givenAdmin(AdminStatus status) {
        when(adminQueryService.findCredential(LOGIN_ID))
                .thenReturn(Optional.of(new AdminCredentialResult(ADMIN_ID, HASH, status)));
    }

    private void assertError(ThrowingCallable callable, ErrorCode errorCode) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
