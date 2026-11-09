package com.shineunsigan.snsg.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.user.application.dto.result.NicknameAvailabilityResult;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.NicknameUnavailableReason;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;
import com.shineunsigan.snsg.user.domain.repository.UserSocialAccountRepository;

/**
 * 닉네임 사용 가능 여부의 판정 순서(형식 → 본인 → 중복)를 본다.
 * 중복 판정 자체(사용 중·보호 중·만료)는 {@code UserRepositoryImplTest}가 실제 DB로 본다.
 */
@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;

    @InjectMocks
    private UserQueryService userQueryService;

    private User userWithNickname(String nickname) {
        return User.create("김민트", "", Profile.of(nickname, Gender.FEMALE, LocalDate.of(1996, 4, 12), null),
                LocalDate.now());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "한", "열한글자가넘는닉네임임", "mint!", "민트 초코"})
    @DisplayName("형식이 틀리면 INVALID_FORMAT이고 저장소를 보지 않는다")
    void invalidFormat(String nickname) {
        NicknameAvailabilityResult result = userQueryService.checkNicknameAvailability(nickname, USER_ID);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(NicknameUnavailableReason.INVALID_FORMAT);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("사용 중이거나 보호 중인 닉네임이면 DUPLICATED")
    void duplicated() {
        when(userRepository.isNicknameTaken(eq("민트초코"), any(LocalDateTime.class))).thenReturn(true);

        NicknameAvailabilityResult result = userQueryService.checkNicknameAvailability("민트초코", null);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(NicknameUnavailableReason.DUPLICATED);
    }

    @Test
    @DisplayName("아무도 쓰지 않고 보호 기간도 지났으면 사용 가능이고 reason은 null")
    void available() {
        when(userRepository.isNicknameTaken(eq("민트초코"), any(LocalDateTime.class))).thenReturn(false);

        NicknameAvailabilityResult result = userQueryService.checkNicknameAvailability("민트초코", null);

        assertThat(result.available()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("본인의 현재 닉네임이면 대소문자가 달라도 사용 가능이고 중복 판정을 하지 않는다")
    void ownCurrentNickname() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithNickname("Mint")));

        NicknameAvailabilityResult result = userQueryService.checkNicknameAvailability("mint", USER_ID);

        assertThat(result.available()).isTrue();
        assertThat(result.reason()).isNull();
        verify(userRepository, never()).isNicknameTaken(anyString(), any());
    }

    @Test
    @DisplayName("토큰의 사용자를 찾지 못하면(탈퇴 포함) 익명과 같이 중복 판정 결과를 따른다")
    void unknownUserFollowsDuplicateCheck() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.isNicknameTaken(eq("민트초코"), any(LocalDateTime.class))).thenReturn(true);

        NicknameAvailabilityResult result = userQueryService.checkNicknameAvailability("민트초코", USER_ID);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(NicknameUnavailableReason.DUPLICATED);
    }

    @Test
    @DisplayName("토큰의 사용자가 있어도 다른 닉네임이면 중복 판정 결과를 따른다")
    void otherNicknameOfUserFollowsDuplicateCheck() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithNickname("레몬티")));
        when(userRepository.isNicknameTaken(eq("민트초코"), any(LocalDateTime.class))).thenReturn(false);

        NicknameAvailabilityResult result = userQueryService.checkNicknameAvailability("민트초코", USER_ID);

        assertThat(result.available()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("userId가 없으면(익명) 사용자를 찾지 않고 중복 판정 결과를 따른다")
    void anonymousFollowsDuplicateCheck() {
        when(userRepository.isNicknameTaken(eq("민트초코"), any(LocalDateTime.class))).thenReturn(true);

        NicknameAvailabilityResult result = userQueryService.checkNicknameAvailability("민트초코", null);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(NicknameUnavailableReason.DUPLICATED);
        verify(userRepository, never()).findById(any());
    }
}
