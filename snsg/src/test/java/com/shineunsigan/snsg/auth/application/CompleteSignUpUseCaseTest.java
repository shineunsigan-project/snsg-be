package com.shineunsigan.snsg.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.auth.application.dto.command.RegisterUserCommand;
import com.shineunsigan.snsg.auth.application.dto.command.SignUpCommand;
import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.application.port.out.SignUpSessionPort;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.constants.Gender;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class CompleteSignUpUseCaseTest {

    private static final String SIGNUP_TOKEN = "signup-token";
    private static final SocialUserResult SOCIAL_USER =
            new SocialUserResult(SocialProvider.KAKAO, "kakao-1", "민트초코", "https://kakao.test/me.png");
    private static final SignUpCommand COMMAND =
            new SignUpCommand(SIGNUP_TOKEN, "김민트", "", "민트라떼", Gender.FEMALE, LocalDate.of(1996, 4, 12), null);

    @Mock
    private SignUpSessionPort signUpSessionPort;

    @Mock
    private UserPort userPort;

    @Mock
    private TokenPort tokenPort;

    @Mock
    private RefreshTokenStorePort refreshTokenStorePort;

    private CompleteSignUpUseCase completeSignUpUseCase;

    @BeforeEach
    void setUp() {
        completeSignUpUseCase = new CompleteSignUpUseCase(signUpSessionPort, userPort,
                new TokenIssuer(tokenPort, refreshTokenStorePort));
    }

    @Test
    @DisplayName("소셜 정보는 클라이언트가 아니라 가입 세션에서 꺼내 가입시킨다")
    void usesSocialInfoFromSession() {
        UUID userId = UUID.randomUUID();
        when(signUpSessionPort.find(SIGNUP_TOKEN)).thenReturn(Optional.of(SOCIAL_USER));
        when(userPort.register(any())).thenReturn(userId);
        when(tokenPort.issueAccessToken(userId)).thenReturn("access");
        when(tokenPort.issueRefreshToken(userId)).thenReturn("refresh");

        TokenResult result = completeSignUpUseCase.completeSignUp(COMMAND);

        ArgumentCaptor<RegisterUserCommand> captor = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(userPort).register(captor.capture());
        assertThat(captor.getValue().socialUser()).isEqualTo(SOCIAL_USER);
        assertThat(captor.getValue().nickname()).isEqualTo("민트라떼");
        assertThat(result.accessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("가입이 끝나면 가입 토큰을 지워 재사용을 막는다")
    void deletesSessionAfterSignUp() {
        UUID userId = UUID.randomUUID();
        when(signUpSessionPort.find(SIGNUP_TOKEN)).thenReturn(Optional.of(SOCIAL_USER));
        when(userPort.register(any())).thenReturn(userId);

        completeSignUpUseCase.completeSignUp(COMMAND);

        verify(signUpSessionPort).delete(SIGNUP_TOKEN);
    }

    @Test
    @DisplayName("가입 세션이 없거나 만료되면 거부한다")
    void rejectsUnknownSignUpToken() {
        when(signUpSessionPort.find(SIGNUP_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> completeSignUpUseCase.completeSignUp(COMMAND))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN);
        verify(userPort, never()).register(any());
    }
}
