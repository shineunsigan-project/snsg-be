package com.shineunsigan.snsg.user.application.dto.result;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.shineunsigan.snsg.user.domain.constants.AgePhase;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.model.AgeGroup;
import com.shineunsigan.snsg.user.domain.model.User;

/**
 * 내 프로필. 조회와 수정이 같은 형태를 돌려준다.
 *
 * @param nicknameChangeableAt 닉네임을 다시 바꿀 수 있는 시각. 지금 바꿀 수 있으면 {@code null}
 */
public record MyProfileResult(UUID id, String name, String nickname, Gender gender, LocalDate birthDate,
        String email, String profileImageUrl, int ageDecade, AgePhase agePhase,
        LocalDateTime nicknameChangeableAt) {

    /**
     * 연령대는 {@code today} 기준 만 나이로 계산한다.
     */
    public static MyProfileResult of(User user, LocalDateTime nicknameChangeableAt, LocalDate today) {
        AgeGroup ageGroup = AgeGroup.of(user.getProfile().getBirthDate(), today);
        return new MyProfileResult(user.getId(), user.getName(), user.getProfile().getNickname(),
                user.getProfile().getGender(), user.getProfile().getBirthDate(), user.getEmail(),
                user.getProfile().getProfileImageUrl(), ageGroup.decade(), ageGroup.phase(), nicknameChangeableAt);
    }
}
