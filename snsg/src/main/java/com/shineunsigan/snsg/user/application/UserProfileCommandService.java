package com.shineunsigan.snsg.user.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.dto.command.UpdateMyProfileCommand;
import com.shineunsigan.snsg.user.application.dto.result.MyProfileResult;
import com.shineunsigan.snsg.user.application.event.ProfileImageReplacedEvent;
import com.shineunsigan.snsg.user.application.port.out.UserEventPort;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.model.UserNicknameHistory;
import com.shineunsigan.snsg.user.domain.repository.UserNicknameHistoryRepository;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 가입한 사용자의 프로필 관리. 계정 생애주기(가입·탈퇴)는 {@link UserCommandService}가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class UserProfileCommandService {

    private final UserRepository userRepository;
    private final UserNicknameHistoryRepository userNicknameHistoryRepository;
    private final UserEventPort userEventPort;
    private final NicknameProperties nicknameProperties;

    /**
     * 내 프로필을 바꾼다. 생략한 값과 현재와 정확히 같은 값은 바꾸지 않은 것으로 본다.
     * <p>
     * 검사 순서는 형식 → 닉네임 변경 제한 → 닉네임 중복이다. 모든 검사를 마친 뒤에 저장하므로
     * 하나라도 실패하면 아무것도 바뀌지 않는다.
     * 닉네임을 바꾸면 이전 닉네임을 보호하는 이력을 남기고, 사진을 바꾸면 이전 파일을 커밋 뒤에 지우도록 이벤트를 낸다.
     */
    @Transactional
    public MyProfileResult updateMyProfile(UpdateMyProfileCommand command) {
        LocalDateTime now = LocalDateTime.now();
        User user = findUser(command);
        Optional<LocalDateTime> lastChangedAt = userNicknameHistoryRepository.findLastChangedAt(command.userId());
        boolean nicknameChanged = isChanged(command.nickname(), user.getProfile().getNickname());

        validate(command, user, nicknameChanged, lastChangedAt, now);
        String previousImageUrl = user.getProfile().getProfileImageUrl();
        change(command, user, nicknameChanged, now);
        publishImageReplaced(command, previousImageUrl);

        Optional<LocalDateTime> latestChangedAt = nicknameChanged ? Optional.of(now) : lastChangedAt;
        return MyProfileResult.of(user, changeableAt(latestChangedAt, now), LocalDate.now());
    }

    private User findUser(UpdateMyProfileCommand command) {
        return userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    /** 생략({@code null})했거나 현재와 정확히 같으면 바뀌지 않은 것이다. 대소문자만 달라도 바뀐 것이다. */
    private boolean isChanged(String requested, String current) {
        return requested != null && !requested.equals(current);
    }

    private void validateNicknameChangeable(Optional<LocalDateTime> lastChangedAt, LocalDateTime now) {
        if (changeableAt(lastChangedAt, now) != null) {
            throw new BusinessException(UserErrorCode.NICKNAME_CHANGE_RESTRICTED);
        }
    }

    /**
     * 대소문자만 다른 본인의 현재 닉네임은 중복으로 보지 않는다. 중복 판정이 본인의 현재 닉네임에 걸리기 때문이다.
     */
    private void validateNicknameNotTaken(User user, String nickname, LocalDateTime now) {
        if (user.getProfile().getNickname().equalsIgnoreCase(nickname)) {
            return;
        }
        if (userRepository.isNicknameTaken(nickname, now)) {
            throw new BusinessException(UserErrorCode.DUPLICATED_NICKNAME);
        }
    }

    private void recordNicknameChange(User user, String previousNickname, LocalDateTime now) {
        userNicknameHistoryRepository.save(UserNicknameHistory.changed(
                user, previousNickname, now, nicknameProperties.protectAfterChange()));
    }

    private void validate(UpdateMyProfileCommand command, User user, boolean nicknameChanged,
            Optional<LocalDateTime> lastChangedAt, LocalDateTime now) {
        User.validateProfileChange(command.nickname(), command.email(), command.profileImageUrl());
        validateProfileImageNotUsed(command, user);
        if (nicknameChanged) {
            validateNicknameChangeable(lastChangedAt, now);
            validateNicknameNotTaken(user, command.nickname(), now);
        }
    }

    /**
     * 다른 회원이 쓰는 사진 경로는 받지 않는다. 받으면 사진을 바꾸거나 탈퇴할 때 그 회원의 파일이 지워진다.
     * 본인의 현재 사진과 같은 값은 바뀐 것이 아니라 보지 않는다.
     */
    private void validateProfileImageNotUsed(UpdateMyProfileCommand command, User user) {
        if (isChanged(command.profileImageUrl(), user.getProfile().getProfileImageUrl())
                && userRepository.isProfileImageUsedByOthers(command.profileImageUrl(), command.userId())) {
            throw new BusinessException(UserErrorCode.INVALID_PROFILE_IMAGE_URL);
        }
    }

    /** 닉네임 유니크 인덱스 위반(동시 변경 경합)이 커밋 때가 아니라 여기서 DUPLICATED_NICKNAME으로 드러나도록 바로 저장한다. */
    private void change(UpdateMyProfileCommand command, User user, boolean nicknameChanged, LocalDateTime now) {
        String previousNickname = user.getProfile().getNickname();
        user.changeProfile(command.nickname(), command.email(), command.profileImageUrl());
        userRepository.save(user);
        if (nicknameChanged) {
            recordNicknameChange(user, previousNickname, now);
        }
    }

    private void publishImageReplaced(UpdateMyProfileCommand command, String previousImageUrl) {
        if (isChanged(command.profileImageUrl(), previousImageUrl)) {
            userEventPort.publishProfileImageReplaced(new ProfileImageReplacedEvent(command.userId(), previousImageUrl));
        }
    }

    private LocalDateTime changeableAt(Optional<LocalDateTime> lastChangedAt, LocalDateTime now) {
        return UserNicknameHistory.nicknameChangeableAt(lastChangedAt, nicknameProperties.protectAfterChange(), now)
                .orElse(null);
    }
}
