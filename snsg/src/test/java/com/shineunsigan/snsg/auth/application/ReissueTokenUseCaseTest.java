package com.shineunsigan.snsg.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class ReissueTokenUseCaseTest {

    private static final String REFRESH_TOKEN = "refresh-token";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private TokenPort tokenPort;

    @Mock
    private RefreshTokenStorePort refreshTokenStorePort;

    @Mock
    private UserPort userPort;

    private ReissueTokenUseCase reissueTokenUseCase;

    @BeforeEach
    void setUp() {
        reissueTokenUseCase = new ReissueTokenUseCase(tokenPort, refreshTokenStorePort, userPort,
                new TokenIssuer(tokenPort, refreshTokenStorePort));
    }

    @Test
    @DisplayName("저장된 토큰과 일치하고 사용자가 유효하면 새 토큰을 발급한다")
    void reissue() {
        when(tokenPort.parseUserId(REFRESH_TOKEN)).thenReturn(USER_ID);
        when(refreshTokenStorePort.matches(USER_ID, REFRESH_TOKEN)).thenReturn(true);
        when(userPort.exists(USER_ID)).thenReturn(true);
        when(tokenPort.issueAccessToken(USER_ID)).thenReturn("new-access");
        when(tokenPort.issueRefreshToken(USER_ID)).thenReturn("new-refresh");

        TokenResult result = reissueTokenUseCase.reissue(REFRESH_TOKEN);

        assertThat(result.accessToken()).isEqualTo("new-access");
        verify(refreshTokenStorePort).save(USER_ID, "new-refresh");
    }

    @Test
    @DisplayName("저장된 토큰과 다르면 거부한다")
    void rejectsTokenThatDoesNotMatchStore() {
        when(tokenPort.parseUserId(REFRESH_TOKEN)).thenReturn(USER_ID);
        when(refreshTokenStorePort.matches(USER_ID, REFRESH_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> reissueTokenUseCase.reissue(REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        verify(tokenPort, never()).issueAccessToken(any());
    }

    @Test
    @DisplayName("탈퇴한 사용자면 토큰이 남아 있어도 거부하고 저장된 토큰을 지운다")
    void rejectsWithdrawnUser() {
        when(tokenPort.parseUserId(REFRESH_TOKEN)).thenReturn(USER_ID);
        when(refreshTokenStorePort.matches(USER_ID, REFRESH_TOKEN)).thenReturn(true);
        when(userPort.exists(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> reissueTokenUseCase.reissue(REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenStorePort).delete(USER_ID);
        verify(tokenPort, never()).issueAccessToken(any());
    }

    @Test
    @DisplayName("토큰이 위조·만료면 refresh 오류로 바꿔 알린다")
    void rejectsInvalidToken() {
        when(tokenPort.parseUserId(REFRESH_TOKEN))
                .thenThrow(new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN));

        assertThatThrownBy(() -> reissueTokenUseCase.reissue(REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
}
