package com.shineunsigan.snsg.user.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.dto.result.MyProfileResult;
import com.shineunsigan.snsg.user.application.dto.result.NicknameAvailabilityResult;
import com.shineunsigan.snsg.user.application.dto.result.UserProfileResult;
import com.shineunsigan.snsg.user.application.port.in.FindUserProfilesUseCase;
import com.shineunsigan.snsg.user.application.port.in.FindUserUseCase;
import com.shineunsigan.snsg.user.domain.constants.NicknameUnavailableReason;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.model.UserNicknameHistory;
import com.shineunsigan.snsg.user.domain.model.UserSocialAccount;
import com.shineunsigan.snsg.user.domain.repository.UserNicknameHistoryRepository;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;
import com.shineunsigan.snsg.user.domain.repository.UserSocialAccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService implements FindUserUseCase, FindUserProfilesUseCase {

    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final UserNicknameHistoryRepository userNicknameHistoryRepository;
    private final NicknameProperties nicknameProperties;

    @Override
    public Optional<UUID> findIdBySocialAccount(SocialProvider provider, String providerUserId) {
        return userSocialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(UserSocialAccount::getUser)
                .map(user -> user.getId());
    }

    @Override
    public boolean exists(UUID userId) {
        return userRepository.findById(userId).isPresent();
    }

    /**
     * 삭제된 행까지 한 번에 읽고, 행이 없는 ID는 탈퇴자와 같이 가려 채운다. 연령대는 오늘 기준이다.
     */
    @Override
    public Map<UUID, UserProfileResult> findProfiles(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> distinctIds = new HashSet<>(userIds);
        Map<UUID, User> users = userRepository.findAllByIdIncludingDeleted(distinctIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return toProfiles(distinctIds, users, LocalDate.now());
    }

    private Map<UUID, UserProfileResult> toProfiles(Set<UUID> ids, Map<UUID, User> users, LocalDate today) {
        Map<UUID, UserProfileResult> profiles = new HashMap<>();
        for (UUID id : ids) {
            User user = users.get(id);
            profiles.put(id, user == null ? UserProfileResult.missing() : UserProfileResult.of(user, today));
        }
        return Map.copyOf(profiles);
    }

    /**
     * 내 프로필. 연령대는 오늘 기준 만 나이로 계산한다.
     * 닉네임을 다시 바꿀 수 있는 시각은 지금 바꿀 수 있으면(바꾼 적이 없을 때 포함) {@code null}이다.
     */
    public MyProfileResult getMyProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        LocalDateTime changeableAt = UserNicknameHistory.nicknameChangeableAt(
                        userNicknameHistoryRepository.findLastChangedAt(userId),
                        nicknameProperties.protectAfterChange(), LocalDateTime.now())
                .orElse(null);
        return MyProfileResult.of(user, changeableAt, LocalDate.now());
    }

    /**
     * 가입·프로필 수정 화면의 안내용이다. 가입·수정이 저장할 때 같은 판정을 다시 한다.
     * 형식 → 본인의 현재 닉네임 → 중복 순서로 본다. {@code userId}가 없으면(익명) 본인 예외가 없다.
     */
    public NicknameAvailabilityResult checkNicknameAvailability(String nickname, UUID userId) {
        if (!Profile.isValidNickname(nickname)) {
            return new NicknameAvailabilityResult(false, NicknameUnavailableReason.INVALID_FORMAT);
        }
        if (isCurrentNicknameOf(userId, nickname)) {
            return new NicknameAvailabilityResult(true, null);
        }
        if (userRepository.isNicknameTaken(nickname, LocalDateTime.now())) {
            return new NicknameAvailabilityResult(false, NicknameUnavailableReason.DUPLICATED);
        }
        return new NicknameAvailabilityResult(true, null);
    }

    /** 사용자를 못 찾으면(탈퇴 포함) 본인 예외가 없다. */
    private boolean isCurrentNicknameOf(UUID userId, String nickname) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> user.getProfile().getNickname().equalsIgnoreCase(nickname))
                .orElse(false);
    }
}
