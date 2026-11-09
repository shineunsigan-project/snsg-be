package com.shineunsigan.snsg.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.dto.result.MyProfileResult;
import com.shineunsigan.snsg.user.application.dto.result.NicknameAvailabilityResult;
import com.shineunsigan.snsg.user.application.dto.result.UserProfileResult;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.NicknameUnavailableReason;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.AgeGroup;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.repository.UserNicknameHistoryRepository;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;
import com.shineunsigan.snsg.user.domain.repository.UserSocialAccountRepository;

/**
 * 닉네임 사용 가능 여부의 판정 순서(형식 → 본인 → 중복)를 본다.
 * 중복 판정 자체(사용 중·보호 중·만료)는 {@code UserRepositoryImplTest}가 실제 DB로 본다.
 * 내 프로필 조회는 닉네임 변경 가능 시각이 마지막 변경 이력에 따라 정해지는지 본다.
 * 타 도메인용 프로필 조회는 정상·삭제·행 없음·중복·빈 입력에서 결과와 저장소 호출을 본다.
 */
@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;

    @Mock
    private UserNicknameHistoryRepository userNicknameHistoryRepository;

    @Spy
    private NicknameProperties nicknameProperties = new NicknameProperties(Duration.ofDays(14));

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

    @Test
    @DisplayName("닉네임을 바꾼 적이 없으면 nicknameChangeableAt은 null")
    void myProfileWithoutHistory() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithNickname("민트초코")));
        when(userNicknameHistoryRepository.findLastChangedAt(USER_ID)).thenReturn(Optional.empty());

        MyProfileResult result = userQueryService.getMyProfile(USER_ID);

        assertThat(result.nickname()).isEqualTo("민트초코");
        assertThat(result.nicknameChangeableAt()).isNull();
    }

    @Test
    @DisplayName("마지막 닉네임 변경이 14일 안이면 nicknameChangeableAt은 변경 시각 + 14일")
    void myProfileWithinRestriction() {
        LocalDateTime lastChangedAt = LocalDateTime.now().minusDays(3);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithNickname("민트초코")));
        when(userNicknameHistoryRepository.findLastChangedAt(USER_ID)).thenReturn(Optional.of(lastChangedAt));

        MyProfileResult result = userQueryService.getMyProfile(USER_ID);

        assertThat(result.nicknameChangeableAt()).isEqualTo(lastChangedAt.plusDays(14));
    }

    @Test
    @DisplayName("마지막 닉네임 변경이 14일보다 전이면 nicknameChangeableAt은 null")
    void myProfileAfterRestriction() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithNickname("민트초코")));
        when(userNicknameHistoryRepository.findLastChangedAt(USER_ID))
                .thenReturn(Optional.of(LocalDateTime.now().minusDays(15)));

        MyProfileResult result = userQueryService.getMyProfile(USER_ID);

        assertThat(result.nicknameChangeableAt()).isNull();
    }

    @Test
    @DisplayName("내 프로필을 조회할 사용자가 없으면(탈퇴 포함) USER_NOT_FOUND")
    void myProfileUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userQueryService.getMyProfile(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("정상 사용자는 저장된 닉네임·사진·성별과 오늘 기준 연령대를 주고 withdrawn은 false")
    void profileOfActiveUser() {
        LocalDate birthDate = LocalDate.of(1996, 4, 12);
        User user = userWithId(USER_ID, "민트초코", "https://k.kakaocdn.net/mint.png", birthDate);
        when(userRepository.findAllByIdIncludingDeleted(anyCollection())).thenReturn(List.of(user));

        Map<UUID, UserProfileResult> profiles = userQueryService.findProfiles(List.of(USER_ID));

        AgeGroup expected = AgeGroup.of(birthDate, LocalDate.now());
        UserProfileResult profile = profiles.get(USER_ID);
        assertThat(profiles).hasSize(1);
        assertThat(profile.nickname()).isEqualTo("민트초코");
        assertThat(profile.profileImageUrl()).isEqualTo("https://k.kakaocdn.net/mint.png");
        assertThat(profile.gender()).isEqualTo(Gender.FEMALE);
        assertThat(profile.ageGroup())
                .isEqualTo(new UserProfileResult.AgeGroupResult(expected.decade(), expected.phase()));
        assertThat(profile.withdrawn()).isFalse();
        verify(userRepository, times(1)).findAllByIdIncludingDeleted(anyCollection());
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("삭제된 사용자는 원본 값이 남아 있어도 닉네임·사진을 가리고 성별만 유지하며 연령대는 null")
    void profileOfDeletedUser() {
        User user = userWithId(USER_ID, "민트초코", "https://k.kakaocdn.net/mint.png", LocalDate.of(1996, 4, 12));
        user.delete();
        when(userRepository.findAllByIdIncludingDeleted(anyCollection())).thenReturn(List.of(user));

        UserProfileResult profile = userQueryService.findProfiles(List.of(USER_ID)).get(USER_ID);

        assertThat(profile.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(profile.profileImageUrl()).isEqualTo(Profile.DEFAULT_PROFILE_IMAGE_URL);
        assertThat(profile.gender()).isEqualTo(Gender.FEMALE);
        assertThat(profile.ageGroup()).isNull();
        assertThat(profile.withdrawn()).isTrue();
        verify(userRepository, times(1)).findAllByIdIncludingDeleted(anyCollection());
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("행이 없는 ID도 결과에 들어가고 탈퇴자로 가려지며 성별·연령대는 null")
    void profileOfMissingUser() {
        UUID missingId = UUID.randomUUID();
        User user = userWithId(USER_ID, "민트초코", null, LocalDate.of(1996, 4, 12));
        when(userRepository.findAllByIdIncludingDeleted(anyCollection())).thenReturn(List.of(user));

        Map<UUID, UserProfileResult> profiles = userQueryService.findProfiles(List.of(USER_ID, missingId));

        UserProfileResult missing = profiles.get(missingId);
        assertThat(profiles).containsOnlyKeys(USER_ID, missingId);
        assertThat(missing.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(missing.profileImageUrl()).isEqualTo(Profile.DEFAULT_PROFILE_IMAGE_URL);
        assertThat(missing.gender()).isNull();
        assertThat(missing.ageGroup()).isNull();
        assertThat(missing.withdrawn()).isTrue();
        assertThat(profiles.get(USER_ID).withdrawn()).isFalse();
        verify(userRepository, times(1)).findAllByIdIncludingDeleted(anyCollection());
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("중복 ID는 저장소에 한 번만 넘기고 결과에도 한 번만 들어간다")
    @SuppressWarnings("unchecked")
    void duplicatedIds() {
        UUID otherId = UUID.randomUUID();
        User user = userWithId(USER_ID, "민트초코", null, LocalDate.of(1996, 4, 12));
        User other = userWithId(otherId, "레몬티", null, LocalDate.of(2000, 1, 1));
        when(userRepository.findAllByIdIncludingDeleted(anyCollection())).thenReturn(List.of(user, other));

        Map<UUID, UserProfileResult> profiles = userQueryService.findProfiles(
                List.of(USER_ID, otherId, USER_ID, otherId, USER_ID));

        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userRepository, times(1)).findAllByIdIncludingDeleted(captor.capture());
        verify(userRepository, never()).findById(any());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(USER_ID, otherId);
        assertThat(profiles).hasSize(2).containsOnlyKeys(USER_ID, otherId);
    }

    @Test
    @DisplayName("빈 입력이면 저장소를 부르지 않고 빈 맵")
    void emptyIds() {
        Map<UUID, UserProfileResult> profiles = userQueryService.findProfiles(List.of());

        assertThat(profiles).isEmpty();
        verify(userRepository, never()).findAllByIdIncludingDeleted(anyCollection());
        verify(userRepository, never()).findById(any());
    }

    /** {@code User.create}로 만든 엔티티는 id가 없어(JPA가 만든다) 맵 키를 보려고 직접 넣는다. */
    private User userWithId(UUID id, String nickname, String profileImageUrl, LocalDate birthDate) {
        User user = User.create("김민트", "", Profile.of(nickname, Gender.FEMALE, birthDate, profileImageUrl),
                LocalDate.now());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
