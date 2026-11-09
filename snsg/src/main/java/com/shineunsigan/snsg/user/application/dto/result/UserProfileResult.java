package com.shineunsigan.snsg.user.application.dto.result;

import java.time.LocalDate;

import com.shineunsigan.snsg.user.domain.constants.AgePhase;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.model.AgeGroup;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.User;

/**
 * 타 도메인에 보여줄 프로필. 이름·이메일·생년월일은 담지 않는다.
 * 탈퇴 여부는 {@code isDeleted}만 보고 정한다. 저장된 값이 비식별됐는지에 기대지 않는다.
 *
 * @param gender    탈퇴자도 저장된 성별을 그대로 준다. 행이 없는 ID면 {@code null}
 * @param ageGroup  탈퇴자·행이 없는 ID면 {@code null}
 * @param withdrawn 탈퇴했거나 행이 없는 ID면 {@code true}
 */
public record UserProfileResult(String nickname, String profileImageUrl, Gender gender, AgeGroupResult ageGroup,
        boolean withdrawn) {

    /**
     * @param decade 십의 자리 × 10 (20대면 20)
     */
    public record AgeGroupResult(int decade, AgePhase phase) {
    }

    /**
     * 연령대는 {@code today} 기준 만 나이로 계산한다. 삭제된 사용자는 계산하지 않고 가린다.
     */
    public static UserProfileResult of(User user, LocalDate today) {
        Profile profile = user.getProfile();
        if (user.isDeleted()) {
            return new UserProfileResult(Profile.WITHDRAWN_NICKNAME, Profile.DEFAULT_PROFILE_IMAGE_URL,
                    profile.getGender(), null, true);
        }
        AgeGroup ageGroup = AgeGroup.of(profile.getBirthDate(), today);
        return new UserProfileResult(profile.getNickname(), profile.getProfileImageUrl(), profile.getGender(),
                new AgeGroupResult(ageGroup.decade(), ageGroup.phase()), false);
    }

    /**
     * 행이 없는 ID. 탈퇴자와 같이 가리고, 성별은 알 수 없어 {@code null}이다.
     */
    public static UserProfileResult missing() {
        return new UserProfileResult(Profile.WITHDRAWN_NICKNAME, Profile.DEFAULT_PROFILE_IMAGE_URL, null, null,
                true);
    }
}
