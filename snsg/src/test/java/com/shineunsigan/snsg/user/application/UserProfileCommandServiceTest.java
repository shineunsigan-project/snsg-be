package com.shineunsigan.snsg.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.user.application.dto.command.UpdateMyProfileCommand;
import com.shineunsigan.snsg.user.application.dto.result.MyProfileResult;
import com.shineunsigan.snsg.user.application.event.ProfileImageReplacedEvent;
import com.shineunsigan.snsg.user.application.port.out.UserEventPort;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.NicknameHistoryReason;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.model.UserNicknameHistory;
import com.shineunsigan.snsg.user.domain.repository.UserNicknameHistoryRepository;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;

/**
 * 프로필 수정의 검사 순서(형식 → 변경 제한 → 중복), 원자성, 이력·이벤트가 남는 경우를 본다.
 * 변경 가능 시각 계산 자체는 {@code UserNicknameHistoryTest}가 본다.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileCommandServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Duration PROTECT_PERIOD = Duration.ofDays(14);
    private static final String OLD_IMAGE = "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png";
    private static final String NEW_IMAGE = "/images/profiles/1b2c3d4e-5f60-7182-93a4-b5c6d7e8f9a0.jpg";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserNicknameHistoryRepository userNicknameHistoryRepository;

    @Mock
    private UserEventPort userEventPort;

    private UserProfileCommandService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new UserProfileCommandService(userRepository, userNicknameHistoryRepository, userEventPort,
                new NicknameProperties(PROTECT_PERIOD, Duration.ofDays(90)));
        user = User.create("김민트", "mint@example.com",
                Profile.of("Mint", Gender.FEMALE, LocalDate.of(1996, 4, 12), OLD_IMAGE), LocalDate.now());
    }

    private void givenUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void givenLastChangedAt(LocalDateTime lastChangedAt) {
        when(userNicknameHistoryRepository.findLastChangedAt(USER_ID)).thenReturn(Optional.ofNullable(lastChangedAt));
    }

    private MyProfileResult update(String nickname, String email, String profileImageUrl) {
        return service.updateMyProfile(new UpdateMyProfileCommand(USER_ID, nickname, email, profileImageUrl));
    }

    @Test
    @DisplayName("세 필드를 모두 생략하면 아무것도 바뀌지 않고 이력·이벤트도 없다")
    void nothingChanged() {
        givenUser();
        givenLastChangedAt(null);

        MyProfileResult result = update(null, null, null);

        assertThat(user.getProfile().getNickname()).isEqualTo("Mint");
        assertThat(user.getEmail()).isEqualTo("mint@example.com");
        assertThat(user.getProfile().getProfileImageUrl()).isEqualTo(OLD_IMAGE);
        assertThat(result.nicknameChangeableAt()).isNull();
        verify(userNicknameHistoryRepository, never()).save(any());
        verify(userEventPort, never()).publishProfileImageReplaced(any());
    }

    @Test
    @DisplayName("현재와 정확히 같은 닉네임은 14일 안이어도 성공하고 이력을 남기지 않는다")
    void sameNicknameWithinRestriction() {
        givenUser();
        LocalDateTime lastChangedAt = LocalDateTime.now().minusDays(1);
        givenLastChangedAt(lastChangedAt);

        MyProfileResult result = update("Mint", null, null);

        assertThat(result.nickname()).isEqualTo("Mint");
        assertThat(result.nicknameChangeableAt()).isEqualTo(lastChangedAt.plusDays(14));
        verify(userNicknameHistoryRepository, never()).save(any());
        verify(userRepository, never()).isNicknameTaken(anyString(), any());
    }

    @Test
    @DisplayName("마지막 변경 후 14일 안에 다른 닉네임으로 바꾸면 NICKNAME_CHANGE_RESTRICTED")
    void restricted() {
        givenUser();
        givenLastChangedAt(LocalDateTime.now().minusDays(13));

        assertErrorCode(() -> update("Lemon", null, null), UserErrorCode.NICKNAME_CHANGE_RESTRICTED);

        assertThat(user.getProfile().getNickname()).isEqualTo("Mint");
        verify(userNicknameHistoryRepository, never()).save(any());
        verify(userRepository, never()).isNicknameTaken(anyString(), any());
    }

    @Test
    @DisplayName("가입 후 첫 변경은 제한 없이 바뀌고, 이전 닉네임을 14일 보호하는 CHANGED 이력이 남는다")
    void firstChange() {
        givenUser();
        givenLastChangedAt(null);
        when(userRepository.isNicknameTaken(eq("Lemon"), any(LocalDateTime.class))).thenReturn(false);
        LocalDateTime before = LocalDateTime.now();

        MyProfileResult result = update("Lemon", null, null);

        LocalDateTime after = LocalDateTime.now();
        assertThat(user.getProfile().getNickname()).isEqualTo("Lemon");
        UserNicknameHistory history = capturedHistory();
        assertThat(history.getNickname()).isEqualTo("Mint");
        assertThat(history.getReason()).isEqualTo(NicknameHistoryReason.CHANGED);
        assertThat(history.getReservedUntil()).isBetween(before.plusDays(14), after.plusDays(14));
        assertThat(result.nicknameChangeableAt()).isNotNull()
                .isCloseTo(LocalDateTime.now().plusDays(14), within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("마지막 변경 후 14일이 지났으면 다시 바꿀 수 있다")
    void afterRestriction() {
        givenUser();
        givenLastChangedAt(LocalDateTime.now().minusDays(15));
        when(userRepository.isNicknameTaken(eq("Lemon"), any(LocalDateTime.class))).thenReturn(false);

        update("Lemon", null, null);

        assertThat(user.getProfile().getNickname()).isEqualTo("Lemon");
        verify(userNicknameHistoryRepository).save(any());
    }

    @Test
    @DisplayName("다른 회원이 쓰거나 보호 중인 닉네임이면 DUPLICATED_NICKNAME이고 이력을 남기지 않는다")
    void duplicated() {
        givenUser();
        givenLastChangedAt(null);
        when(userRepository.isNicknameTaken(eq("Lemon"), any(LocalDateTime.class))).thenReturn(true);

        assertErrorCode(() -> update("Lemon", null, null), UserErrorCode.DUPLICATED_NICKNAME);

        assertThat(user.getProfile().getNickname()).isEqualTo("Mint");
        verify(userNicknameHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("대소문자만 다른 닉네임은 본인 닉네임에 막히지 않고 변경되며 이력이 남는다")
    void caseOnlyChange() {
        givenUser();
        givenLastChangedAt(null);

        update("mint", null, null);

        assertThat(user.getProfile().getNickname()).isEqualTo("mint");
        verify(userRepository, never()).isNicknameTaken(anyString(), any());
        assertThat(capturedHistory().getNickname()).isEqualTo("Mint");
    }

    @Test
    @DisplayName("대소문자만 다른 닉네임도 변경이라 14일 안이면 NICKNAME_CHANGE_RESTRICTED")
    void caseOnlyChangeWithinRestriction() {
        givenUser();
        givenLastChangedAt(LocalDateTime.now().minusDays(1));

        assertErrorCode(() -> update("mint", null, null), UserErrorCode.NICKNAME_CHANGE_RESTRICTED);

        assertThat(user.getProfile().getNickname()).isEqualTo("Mint");
    }

    @Test
    @DisplayName("유효한 새 닉네임과 틀린 이메일을 보내면 INVALID_EMAIL이고 아무것도 저장하지 않는다")
    void invalidEmailRollsBackNickname() {
        givenUser();
        givenLastChangedAt(null);

        assertErrorCode(() -> update("Lemon", "mint@", NEW_IMAGE), UserErrorCode.INVALID_EMAIL);

        assertThat(user.getProfile().getNickname()).isEqualTo("Mint");
        assertThat(user.getProfile().getProfileImageUrl()).isEqualTo(OLD_IMAGE);
        verify(userNicknameHistoryRepository, never()).save(any());
        verify(userEventPort, never()).publishProfileImageReplaced(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("형식 오류는 변경 제한보다 먼저 알린다")
    void formatCheckedBeforeRestriction() {
        givenUser();
        givenLastChangedAt(LocalDateTime.now().minusDays(1));

        assertErrorCode(() -> update("Lemon", "mint@", null), UserErrorCode.INVALID_EMAIL);
    }

    @Test
    @DisplayName("이메일을 빈 문자열로 보내면 지운다")
    void clearEmail() {
        givenUser();
        givenLastChangedAt(null);

        MyProfileResult result = update(null, "", null);

        assertThat(user.getEmail()).isEmpty();
        assertThat(result.email()).isEmpty();
    }

    @Test
    @DisplayName("이메일을 생략하면 그대로다")
    void keepEmail() {
        givenUser();
        givenLastChangedAt(null);

        update(null, null, null);

        assertThat(user.getEmail()).isEqualTo("mint@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://evil.test/me.png"})
    @DisplayName("업로드 경로가 아닌 사진이면 INVALID_PROFILE_IMAGE_URL이고 아무것도 바뀌지 않는다")
    void invalidProfileImageUrl(String profileImageUrl) {
        givenUser();
        givenLastChangedAt(null);

        assertErrorCode(() -> update("Lemon", "lemon@example.com", profileImageUrl),
                UserErrorCode.INVALID_PROFILE_IMAGE_URL);

        assertThat(user.getProfile().getNickname()).isEqualTo("Mint");
        assertThat(user.getEmail()).isEqualTo("mint@example.com");
        assertThat(user.getProfile().getProfileImageUrl()).isEqualTo(OLD_IMAGE);
        verify(userNicknameHistoryRepository, never()).save(any());
        verify(userEventPort, never()).publishProfileImageReplaced(any());
    }

    @Test
    @DisplayName("다른 업로드 경로로 바꾸면 사진이 바뀌고 이전 경로로 ProfileImageReplacedEvent를 한 번 낸다")
    void replaceImage() {
        givenUser();
        givenLastChangedAt(null);

        update(null, null, NEW_IMAGE);

        assertThat(user.getProfile().getProfileImageUrl()).isEqualTo(NEW_IMAGE);
        verify(userEventPort).publishProfileImageReplaced(new ProfileImageReplacedEvent(USER_ID, OLD_IMAGE));
    }

    @Test
    @DisplayName("같은 사진 경로면 이벤트를 내지 않는다")
    void sameImage() {
        givenUser();
        givenLastChangedAt(null);

        update(null, null, OLD_IMAGE);

        verify(userEventPort, never()).publishProfileImageReplaced(any());
        verify(userRepository, never()).isProfileImageUsedByOthers(anyString(), any());
    }

    @Test
    @DisplayName("다른 회원이 쓰는 업로드 경로면 INVALID_PROFILE_IMAGE_URL이고 아무것도 바뀌지 않으며 이벤트도 없다")
    void imageUsedByOthers() {
        givenUser();
        givenLastChangedAt(null);
        when(userRepository.isProfileImageUsedByOthers(NEW_IMAGE, USER_ID)).thenReturn(true);

        assertErrorCode(() -> update("Lemon", "lemon@example.com", NEW_IMAGE),
                UserErrorCode.INVALID_PROFILE_IMAGE_URL);

        assertThat(user.getProfile().getNickname()).isEqualTo("Mint");
        assertThat(user.getEmail()).isEqualTo("mint@example.com");
        assertThat(user.getProfile().getProfileImageUrl()).isEqualTo(OLD_IMAGE);
        verify(userNicknameHistoryRepository, never()).save(any());
        verify(userEventPort, never()).publishProfileImageReplaced(any());
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/images/profiles/..", "/images/profiles/", "/images/profiles/a.png"})
    @DisplayName("접두사는 맞지만 업로드 경로 형식이 아니면 INVALID_PROFILE_IMAGE_URL")
    void malformedUploadedPath(String profileImageUrl) {
        givenUser();
        givenLastChangedAt(null);

        assertErrorCode(() -> update(null, null, profileImageUrl), UserErrorCode.INVALID_PROFILE_IMAGE_URL);

        assertThat(user.getProfile().getProfileImageUrl()).isEqualTo(OLD_IMAGE);
        verify(userEventPort, never()).publishProfileImageReplaced(any());
    }

    @Test
    @DisplayName("사용자가 없으면(탈퇴 포함) USER_NOT_FOUND")
    void userNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertErrorCode(() -> update("Lemon", null, null), UserErrorCode.USER_NOT_FOUND);
    }

    private UserNicknameHistory capturedHistory() {
        ArgumentCaptor<UserNicknameHistory> captor = ArgumentCaptor.forClass(UserNicknameHistory.class);
        verify(userNicknameHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private void assertErrorCode(ThrowingCallable callable, ErrorCode errorCode) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
