package com.shineunsigan.snsg.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.dto.command.RegisterUserCommand;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.repository.TermsAgreementRepository;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;
import com.shineunsigan.snsg.user.domain.repository.UserSocialAccountRepository;

/**
 * 가입이 입력을 저장하고, 중복 닉네임·남이 쓰는 사진 경로를 막는지 본다. 탈퇴는 {@code UserWithdrawalCommandServiceTest}가 본다.
 */
@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    private static final String PROVIDER_USER_ID = "kakao-1";
    private static final String UPLOADED_IMAGE = "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;

    @Mock
    private TermsAgreementRepository termsAgreementRepository;

    @Mock
    private TermsProperties termsProperties;

    @InjectMocks
    private UserCommandService userCommandService;

    private RegisterUserCommand registerCommand(String nickname, String email) {
        return new RegisterUserCommand(SocialProvider.KAKAO, PROVIDER_USER_ID, "김민트", email, nickname,
                Gender.FEMALE, LocalDate.of(1996, 4, 12), UPLOADED_IMAGE);
    }

    private User registerAndCaptureSavedUser(RegisterUserCommand command) {
        when(userRepository.isNicknameTaken(eq(command.nickname()), any(LocalDateTime.class))).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userCommandService.register(command);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("가입하면 이름과 이메일이 저장된다")
    void registerStoresNameAndEmail() {
        User saved = registerAndCaptureSavedUser(registerCommand("민트초코", "User@Example.com"));

        assertThat(saved.getName()).isEqualTo("김민트");
        assertThat(saved.getEmail()).isEqualTo("User@Example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("이메일을 생략하거나 빈 문자열로 보내면 빈 문자열로 저장된다")
    void registerStoresEmptyEmail(String email) {
        User saved = registerAndCaptureSavedUser(registerCommand("민트초코", email));

        assertThat(saved.getEmail()).isEmpty();
    }

    @Test
    @DisplayName("이미 쓰이거나 보호 중인 닉네임이면 저장하지 않고 DUPLICATED_NICKNAME")
    void registerRejectsDuplicatedNickname() {
        when(userRepository.isNicknameTaken(eq("mint"), any(LocalDateTime.class))).thenReturn(true);

        assertThatThrownBy(() -> userCommandService.register(registerCommand("mint", "")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.DUPLICATED_NICKNAME);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("가입에서 다른 회원이 쓰는 업로드 경로면 저장하지 않고 INVALID_PROFILE_IMAGE_URL")
    void registerRejectsProfileImageUsedByOthers() {
        when(userRepository.isNicknameTaken(eq("민트초코"), any(LocalDateTime.class))).thenReturn(false);
        when(userRepository.isProfileImageUsedByOthers(UPLOADED_IMAGE, null)).thenReturn(true);

        assertThatThrownBy(() -> userCommandService.register(registerCommand("민트초코", "")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_PROFILE_IMAGE_URL);
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/images/profiles/..", "/images/profiles/", "/images/profiles/a.png"})
    @DisplayName("가입에서 접두사는 맞지만 업로드 경로 형식이 아니면 저장하지 않고 INVALID_PROFILE_IMAGE_URL")
    void registerRejectsMalformedUploadedPath(String profileImageUrl) {
        when(userRepository.isNicknameTaken(eq("민트초코"), any(LocalDateTime.class))).thenReturn(false);

        assertThatThrownBy(() -> userCommandService.register(registerCommandWithImage(profileImageUrl)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_PROFILE_IMAGE_URL);
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"https://k.kakaocdn.net/dn/abc/img_640x640.jpg"})
    @DisplayName("가입에서 소셜 사진 주소와 null은 기존대로 받고 사용 여부를 보지 않는다")
    void registerAcceptsSocialImageAndNull(String profileImageUrl) {
        User saved = registerAndCaptureSavedUser(registerCommandWithImage(profileImageUrl));

        assertThat(saved.getProfile().getProfileImageUrl())
                .isEqualTo(profileImageUrl == null ? Profile.DEFAULT_PROFILE_IMAGE_URL : profileImageUrl);
        verify(userRepository, never()).isProfileImageUsedByOthers(any(), any());
    }

    private RegisterUserCommand registerCommandWithImage(String profileImageUrl) {
        return new RegisterUserCommand(SocialProvider.KAKAO, PROVIDER_USER_ID, "김민트", "", "민트초코",
                Gender.FEMALE, LocalDate.of(1996, 4, 12), profileImageUrl);
    }
}
