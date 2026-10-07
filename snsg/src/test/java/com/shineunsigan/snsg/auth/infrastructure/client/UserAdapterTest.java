package com.shineunsigan.snsg.auth.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.auth.application.dto.command.RegisterUserCommand;
import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.domain.constants.Gender;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.application.port.in.FindUserUseCase;
import com.shineunsigan.snsg.user.application.port.in.RegisterUserUseCase;

/**
 * auth와 user는 같은 이름의 enum을 각자 갖는다. 어댑터가 올바른 값으로 바꾸는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class UserAdapterTest {

    @Mock
    private FindUserUseCase findUserUseCase;

    @Mock
    private RegisterUserUseCase registerUserUseCase;

    @InjectMocks
    private UserAdapter userAdapter;

    @Test
    @DisplayName("소셜 제공자를 user 도메인 값으로 바꿔 조회한다")
    void convertsProviderOnFind() {
        UUID userId = UUID.randomUUID();
        when(findUserUseCase.findIdBySocialAccount(
                com.shineunsigan.snsg.user.domain.constants.SocialProvider.KAKAO, "kakao-1"))
                .thenReturn(Optional.of(userId));

        assertThat(userAdapter.findIdBySocialAccount(SocialProvider.KAKAO, "kakao-1")).contains(userId);
    }

    @Test
    @DisplayName("가입 요청의 제공자와 성별을 user 도메인 값으로 바꿔 넘긴다")
    void convertsProviderAndGenderOnRegister() {
        RegisterUserCommand command = new RegisterUserCommand(
                new SocialUserResult(SocialProvider.NAVER, "naver-1", "민트초코", "https://naver.test/me.png"),
                "민트라떼", Gender.FEMALE, LocalDate.of(1996, 4, 12), "https://naver.test/me.png");
        when(registerUserUseCase.register(any())).thenReturn(UUID.randomUUID());

        userAdapter.register(command);

        ArgumentCaptor<com.shineunsigan.snsg.user.application.dto.command.RegisterUserCommand> captor =
                ArgumentCaptor.forClass(com.shineunsigan.snsg.user.application.dto.command.RegisterUserCommand.class);
        verify(registerUserUseCase).register(captor.capture());

        var userCommand = captor.getValue();
        assertThat(userCommand.provider())
                .isEqualTo(com.shineunsigan.snsg.user.domain.constants.SocialProvider.NAVER);
        assertThat(userCommand.gender())
                .isEqualTo(com.shineunsigan.snsg.user.domain.constants.Gender.FEMALE);
        assertThat(userCommand.providerUserId()).isEqualTo("naver-1");
        assertThat(userCommand.nickname()).isEqualTo("민트라떼");
        assertThat(userCommand.profileImageUrl()).isEqualTo("https://naver.test/me.png");
    }
}
