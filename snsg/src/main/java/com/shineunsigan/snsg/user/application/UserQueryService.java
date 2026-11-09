package com.shineunsigan.snsg.user.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.dto.result.MyProfileResult;
import com.shineunsigan.snsg.user.application.dto.result.NicknameAvailabilityResult;
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
public class UserQueryService implements FindUserUseCase {

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
