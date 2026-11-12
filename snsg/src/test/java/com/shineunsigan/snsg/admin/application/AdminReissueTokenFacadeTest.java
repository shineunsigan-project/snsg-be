package com.shineunsigan.snsg.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.admin.application.dto.result.AdminRefreshTokenClaims;
import com.shineunsigan.snsg.admin.application.dto.result.AdminTokenResult;
import com.shineunsigan.snsg.admin.application.port.out.AdminRefreshTokenStorePort;
import com.shineunsigan.snsg.admin.application.port.out.AdminTokenPort;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class AdminReissueTokenFacadeTest {

    private static final String REFRESH_TOKEN = "refresh-token";
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String SESSION_ID = "session-a";
    private static final Duration ACCESS_VALIDITY = Duration.ofMinutes(30);
    /** 로그인 후 11시간이 지나 세션이 1시간 남은 상태. */
    private static final Instant SESSION_EXPIRES_AT = Instant.now().plus(Duration.ofHours(1));
    private static final AdminRefreshTokenClaims CLAIMS =
            new AdminRefreshTokenClaims(ADMIN_ID, SESSION_ID, SESSION_EXPIRES_AT);

    @Mock
    private AdminTokenPort adminTokenPort;

    @Mock
    private AdminRefreshTokenStorePort adminRefreshTokenStorePort;

    @Mock
    private AdminQueryService adminQueryService;

    private AdminReissueTokenFacade facade;

    @BeforeEach
    void setUp() {
        facade = new AdminReissueTokenFacade(adminTokenPort, adminRefreshTokenStorePort, adminQueryService,
                new AdminTokenIssuer(adminTokenPort, adminRefreshTokenStorePort,
                        new AdminProperties(ACCESS_VALIDITY, Duration.ofHours(12), 5, Duration.ofMinutes(15), 20, 50)));
    }

    @Nested
    @DisplayName("재발급")
    class Reissue {

        @Test
        @DisplayName("일치하고 승인된 어드민이면 같은 세션으로 회전한다. 세션 만료는 늘지 않고 처음 값을 이어받는다")
        void rotateKeepsSessionExpiry() {
            givenValidSession(CLAIMS);
            when(adminTokenPort.issueAccessToken(any(), any())).thenReturn("new-access");
            when(adminTokenPort.issueRefreshToken(ADMIN_ID, SESSION_ID, SESSION_EXPIRES_AT)).thenReturn("new-refresh");

            AdminTokenResult result = facade.reissue(REFRESH_TOKEN);

            assertThat(result).isEqualTo(new AdminTokenResult("new-access", "new-refresh", SESSION_EXPIRES_AT));
            verify(adminRefreshTokenStorePort).save(ADMIN_ID, SESSION_ID, "new-refresh", SESSION_EXPIRES_AT);
            assertThat(capturedAccessExpiry()).isBefore(SESSION_EXPIRES_AT)
                    .isBeforeOrEqualTo(Instant.now().plus(ACCESS_VALIDITY));
        }

        @Test
        @DisplayName("세션이 access 수명보다 적게 남았으면 access도 세션 만료에 끝난다")
        void accessDoesNotOutliveSession() {
            Instant soon = Instant.now().plus(Duration.ofMinutes(10));
            givenValidSession(new AdminRefreshTokenClaims(ADMIN_ID, SESSION_ID, soon));
            when(adminTokenPort.issueAccessToken(any(), any())).thenReturn("new-access");
            when(adminTokenPort.issueRefreshToken(ADMIN_ID, SESSION_ID, soon)).thenReturn("new-refresh");

            facade.reissue(REFRESH_TOKEN);

            assertThat(capturedAccessExpiry()).isEqualTo(soon);
        }

        @Test
        @DisplayName("세션 만료가 지났으면 INVALID_ADMIN_REFRESH_TOKEN이고 발급하지 않는다")
        void expiredSession() {
            when(adminTokenPort.parseRefreshToken(REFRESH_TOKEN)).thenReturn(
                    new AdminRefreshTokenClaims(ADMIN_ID, SESSION_ID, Instant.now().minusSeconds(1)));

            assertInvalid();
            verify(adminTokenPort, never()).issueRefreshToken(any(), anyString(), any());
            verify(adminRefreshTokenStorePort, never()).save(any(), anyString(), anyString(), any());
        }

        private void givenValidSession(AdminRefreshTokenClaims claims) {
            when(adminTokenPort.parseRefreshToken(REFRESH_TOKEN)).thenReturn(claims);
            when(adminRefreshTokenStorePort.matches(ADMIN_ID, SESSION_ID, REFRESH_TOKEN)).thenReturn(true);
            when(adminQueryService.isApproved(ADMIN_ID)).thenReturn(true);
        }

        private Instant capturedAccessExpiry() {
            ArgumentCaptor<Instant> expiry = ArgumentCaptor.forClass(Instant.class);
            verify(adminTokenPort).issueAccessToken(eq(ADMIN_ID), expiry.capture());
            return expiry.getValue();
        }

        @Test
        @DisplayName("refresh를 해석할 수 없으면 INVALID_ADMIN_REFRESH_TOKEN")
        void unparsable() {
            when(adminTokenPort.parseRefreshToken(REFRESH_TOKEN))
                    .thenThrow(new BusinessException(AdminErrorCode.INVALID_ADMIN_ACCESS_TOKEN));

            assertInvalid();
            verifyNoInteractions(adminRefreshTokenStorePort, adminQueryService);
        }

        @Test
        @DisplayName("저장된 세션과 다르면(회전됨·로그아웃됨) INVALID_ADMIN_REFRESH_TOKEN이고 발급하지 않는다")
        void sessionMismatch() {
            when(adminTokenPort.parseRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);

            assertInvalid();
            verify(adminRefreshTokenStorePort, never()).save(any(), anyString(), anyString(), any());
            verify(adminRefreshTokenStorePort, never()).deleteAll(any());
        }

        @Test
        @DisplayName("어드민이 승인 상태가 아니면(없음·삭제·승인 대기) 그 어드민의 세션을 모두 지우고 INVALID_ADMIN_REFRESH_TOKEN")
        void notApproved() {
            when(adminTokenPort.parseRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);
            when(adminRefreshTokenStorePort.matches(ADMIN_ID, SESSION_ID, REFRESH_TOKEN)).thenReturn(true);

            assertInvalid();
            verify(adminRefreshTokenStorePort).deleteAll(ADMIN_ID);
            verify(adminRefreshTokenStorePort, never()).save(any(), anyString(), anyString(), any());
        }

        private void assertInvalid() {
            assertThatThrownBy(() -> facade.reissue(REFRESH_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(AdminErrorCode.INVALID_ADMIN_REFRESH_TOKEN);
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("쿠키의 세션이 요청자의 것이면 그 세션 하나만 지운다")
        void deleteOwnSession() {
            when(adminTokenPort.parseRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);

            facade.logout(ADMIN_ID, REFRESH_TOKEN);

            verify(adminRefreshTokenStorePort).delete(ADMIN_ID, SESSION_ID);
            verify(adminRefreshTokenStorePort, never()).deleteAll(any());
        }

        @Test
        @DisplayName("쿠키가 없으면 아무것도 지우지 않는다")
        void noCookie() {
            facade.logout(ADMIN_ID, null);
            facade.logout(ADMIN_ID, " ");

            verifyNoInteractions(adminTokenPort, adminRefreshTokenStorePort);
        }

        @Test
        @DisplayName("해석할 수 없는 쿠키면 아무것도 지우지 않는다")
        void unparsable() {
            when(adminTokenPort.parseRefreshToken(REFRESH_TOKEN))
                    .thenThrow(new BusinessException(AdminErrorCode.INVALID_ADMIN_REFRESH_TOKEN));

            facade.logout(ADMIN_ID, REFRESH_TOKEN);

            verifyNoInteractions(adminRefreshTokenStorePort);
        }

        @Test
        @DisplayName("다른 어드민의 토큰이면 아무것도 지우지 않는다")
        void otherAdminsToken() {
            when(adminTokenPort.parseRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);

            facade.logout(UUID.randomUUID(), REFRESH_TOKEN);

            verifyNoInteractions(adminRefreshTokenStorePort);
        }
    }
}
