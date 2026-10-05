package com.shineunsigan.snsg.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.auth.application.dto.result.SocialAuthResult;
import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.application.port.out.SignUpSessionPort;
import com.shineunsigan.snsg.auth.application.port.out.SocialLoginPort;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;

/**
 * 소셜 인증의 분기를 검증한다. 기존 회원이면 로그인, 신규면 가입 토큰이다.
 * 신규 사용자를 여기서 만들지 않는 것이 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
class SocialLoginUseCaseTest {

    private static final String AUTH_CODE = "auth-code";
    private static final SocialUserResult SOCIAL_USER =
            new SocialUserResult(SocialProvider.KAKAO, "kakao-1", "민트초코", "https://kakao.test/me.png");

    @Mock
    private SocialLoginPort socialLoginPort;

    @Mock
    private UserPort userPort;

    @Mock
    private SignUpSessionPort signUpSessionPort;

    @Mock
    private TokenPort tokenPort;

    @Mock
    private RefreshTokenStorePort refreshTokenStorePort;

    private SocialLoginUseCase socialLoginUseCase;

    @BeforeEach
    void setUp() {
        socialLoginUseCase = new SocialLoginUseCase(List.of(socialLoginPort), userPort, signUpSessionPort,
                new TokenIssuer(tokenPort, refreshTokenStorePort));
    }

    @Test
    @DisplayName("이미 가입한 소셜 계정이면 토큰을 발급하고 가입 세션을 만들지 않는다")
    void existingUserLogsIn() {
        UUID userId = UUID.randomUUID();
        givenKakao();
        when(socialLoginPort.fetchUser(AUTH_CODE)).thenReturn(SOCIAL_USER);
        when(userPort.findIdBySocialAccount(SocialProvider.KAKAO, "kakao-1")).thenReturn(Optional.of(userId));
        when(tokenPort.issueAccessToken(userId)).thenReturn("access");
        when(tokenPort.issueRefreshToken(userId)).thenReturn("refresh");

        SocialAuthResult result = socialLoginUseCase.authenticate(SocialProvider.KAKAO, AUTH_CODE);

        assertThat(result).isInstanceOf(SocialAuthResult.LoggedIn.class);
        assertThat(((SocialAuthResult.LoggedIn) result).tokens().accessToken()).isEqualTo("access");
        verify(refreshTokenStorePort).save(userId, "refresh");
        verify(signUpSessionPort, never()).save(any());
    }

    @Test
    @DisplayName("가입하지 않은 소셜 계정이면 가입 토큰만 주고 사용자를 만들지 않는다")
    void newUserNeedsSignUp() {
        givenKakao();
        when(socialLoginPort.fetchUser(AUTH_CODE)).thenReturn(SOCIAL_USER);
        when(userPort.findIdBySocialAccount(SocialProvider.KAKAO, "kakao-1")).thenReturn(Optional.empty());
        when(signUpSessionPort.save(SOCIAL_USER)).thenReturn("signup-token");

        SocialAuthResult result = socialLoginUseCase.authenticate(SocialProvider.KAKAO, AUTH_CODE);

        assertThat(result).isInstanceOf(SocialAuthResult.SignUpRequired.class);
        assertThat(((SocialAuthResult.SignUpRequired) result).signUpToken()).isEqualTo("signup-token");
        verify(userPort, never()).register(any());
        verify(tokenPort, never()).issueAccessToken(any());
    }

    private void givenKakao() {
        when(socialLoginPort.provider()).thenReturn(SocialProvider.KAKAO);
    }
}
