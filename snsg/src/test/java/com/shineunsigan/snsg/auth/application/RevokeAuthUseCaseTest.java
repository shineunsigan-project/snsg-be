package com.shineunsigan.snsg.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.application.port.out.SocialUnlinkPort;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;

@ExtendWith(MockitoExtension.class)
class RevokeAuthUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String PROVIDER_USER_ID = "kakao-1";

    @Mock
    private RefreshTokenStorePort refreshTokenStorePort;

    @Mock
    private SocialUnlinkPort socialUnlinkPort;

    @InjectMocks
    private RevokeAuthUseCase revokeAuthUseCase;

    @Test
    @DisplayName("사용자가 탈퇴하면 토큰을 지우고 소셜 연동도 끊는다")
    void revokeOnUserRequest() {
        revokeAuthUseCase.revoke(USER_ID, SocialProvider.KAKAO, PROVIDER_USER_ID, true);

        verify(refreshTokenStorePort).delete(USER_ID);
        verify(socialUnlinkPort).unlink(SocialProvider.KAKAO, PROVIDER_USER_ID);
    }

    @Test
    @DisplayName("소셜 쪽에서 끊긴 경우에는 연동 해제를 호출하지 않는다")
    void doesNotUnlinkWhenAlreadyUnlinked() {
        revokeAuthUseCase.revoke(USER_ID, SocialProvider.KAKAO, PROVIDER_USER_ID, false);

        verify(refreshTokenStorePort).delete(USER_ID);
        verify(socialUnlinkPort, never()).unlink(any(), anyString());
    }

    @Test
    @DisplayName("연동 해제에 실패해도 탈퇴를 되돌리지 않는다")
    void survivesUnlinkFailure() {
        doThrow(new RuntimeException("카카오 응답 없음"))
                .when(socialUnlinkPort).unlink(SocialProvider.KAKAO, PROVIDER_USER_ID);

        assertThatCode(() -> revokeAuthUseCase.revoke(USER_ID, SocialProvider.KAKAO, PROVIDER_USER_ID, true))
                .doesNotThrowAnyException();

        verify(refreshTokenStorePort).delete(USER_ID);
    }
}
