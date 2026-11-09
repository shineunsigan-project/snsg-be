package com.shineunsigan.snsg.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.auth.application.dto.result.RefreshTokenClaims;
import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class ReissueTokenFacadeTest {

    private static final String REFRESH_TOKEN = "refresh-token";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String SESSION_ID = "session-a";
    private static final RefreshTokenClaims CLAIMS = new RefreshTokenClaims(USER_ID, SESSION_ID);

    @Mock
    private TokenPort tokenPort;

    @Mock
    private RefreshTokenStorePort refreshTokenStorePort;

    @Mock
    private UserPort userPort;

    private ReissueTokenFacade reissueTokenFacade;

    @BeforeEach
    void setUp() {
        reissueTokenFacade = new ReissueTokenFacade(tokenPort, refreshTokenStorePort, userPort,
                new TokenIssuer(tokenPort, refreshTokenStorePort));
    }

    @Nested
    @DisplayName("재발급")
    class Reissue {

        @Test
        @DisplayName("세션의 토큰과 일치하고 사용자가 유효하면 같은 세션으로 새 토큰을 발급한다")
        void reissue() {
            when(tokenPort.parseRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);
            when(refreshTokenStorePort.matches(USER_ID, SESSION_ID, REFRESH_TOKEN)).thenReturn(true);
            when(userPort.exists(USER_ID)).thenReturn(true);
            when(tokenPort.issueAccessToken(USER_ID)).thenReturn("new-access");
            when(tokenPort.issueRefreshToken(USER_ID, SESSION_ID)).thenReturn("new-refresh");

            TokenResult result = reissueTokenFacade.reissue(REFRESH_TOKEN);

            assertThat(result.accessToken()).isEqualTo("new-access");
            assertThat(result.refreshToken()).isEqualTo("new-refresh");
            verify(refreshTokenStorePort).save(USER_ID, SESSION_ID, "new-refresh");
        }

        @Test
        @DisplayName("세션의 토큰과 다르면(이미 교체됨·로그아웃됨·만료됨) 거부한다")
        void rejectsTokenThatDoesNotMatchStore() {
            when(tokenPort.parseRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);
            when(refreshTokenStorePort.matches(USER_ID, SESSION_ID, REFRESH_TOKEN)).thenReturn(false);

            assertThatThrownBy(() -> reissueTokenFacade.reissue(REFRESH_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
            verify(tokenPort, never()).issueAccessToken(any());
            verify(tokenPort, never()).issueRefreshToken(any(), anyString());
        }

        @Test
        @DisplayName("탈퇴한 사용자면 세션이 남아 있어도 거부하고 모든 세션을 지운다")
        void rejectsWithdrawnUser() {
            when(tokenPort.parseRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);
            when(refreshTokenStorePort.matches(USER_ID, SESSION_ID, REFRESH_TOKEN)).thenReturn(true);
            when(userPort.exists(USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> reissueTokenFacade.reissue(REFRESH_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
            verify(refreshTokenStorePort).deleteAll(USER_ID);
            verify(tokenPort, never()).issueAccessToken(any());
            verify(tokenPort, never()).issueRefreshToken(any(), anyString());
        }

        @Test
        @DisplayName("토큰이 위조·만료이거나 세션 식별자가 없으면 refresh 오류로 바꿔 알리고 저장소를 보지 않는다")
        void rejectsInvalidToken() {
            when(tokenPort.parseRefreshToken(REFRESH_TOKEN))
                    .thenThrow(new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN));

            assertThatThrownBy(() -> reissueTokenFacade.reissue(REFRESH_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
            verifyNoInteractions(refreshTokenStorePort);
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("refresh token이 가리키는 그 세션만 지운다")
        void deletesOnlyThatSession() {
            when(tokenPort.parseRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);

            reissueTokenFacade.logout(USER_ID, REFRESH_TOKEN);

            verify(refreshTokenStorePort).delete(USER_ID, SESSION_ID);
            verify(refreshTokenStorePort, never()).deleteAll(any());
        }

        @Test
        @DisplayName("쿠키가 없거나 비어 있으면 아무것도 지우지 않고 끝낸다")
        void doesNothingWithoutCookie() {
            assertThatCode(() -> reissueTokenFacade.logout(USER_ID, null)).doesNotThrowAnyException();
            assertThatCode(() -> reissueTokenFacade.logout(USER_ID, "")).doesNotThrowAnyException();

            verifyNoInteractions(refreshTokenStorePort);
        }

        @Test
        @DisplayName("refresh token을 해석할 수 없으면(위조·만료·이전 형식) 아무것도 지우지 않고 끝낸다")
        void doesNothingWithInvalidToken() {
            when(tokenPort.parseRefreshToken(REFRESH_TOKEN))
                    .thenThrow(new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

            assertThatCode(() -> reissueTokenFacade.logout(USER_ID, REFRESH_TOKEN)).doesNotThrowAnyException();

            verifyNoInteractions(refreshTokenStorePort);
        }

        @Test
        @DisplayName("다른 사용자의 refresh token이면 남의 세션을 지우지 않는다")
        void doesNothingWithOtherUsersToken() {
            when(tokenPort.parseRefreshToken(REFRESH_TOKEN))
                    .thenReturn(new RefreshTokenClaims(UUID.randomUUID(), SESSION_ID));

            assertThatCode(() -> reissueTokenFacade.logout(USER_ID, REFRESH_TOKEN)).doesNotThrowAnyException();

            verifyNoInteractions(refreshTokenStorePort);
        }
    }
}
