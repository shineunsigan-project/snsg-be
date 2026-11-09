package com.shineunsigan.snsg.schedule.application.dto.result;

import com.shineunsigan.snsg.schedule.domain.constants.Gender;
import com.shineunsigan.snsg.schedule.domain.model.ParticipantProfile;

/**
 * schedule이 사용자에 대해 아는 값. 남에게 보여줄 프로필이다.
 * 탈퇴했거나 사용자 정보를 찾을 수 없으면 user가 가린 값(닉네임 "탈퇴한 사용자", 기본 이미지)이 그대로 온다.
 *
 * @param gender    탈퇴자도 저장된 성별이다. 사용자 정보를 찾을 수 없으면 {@code null}
 * @param ageGroup  탈퇴자·사용자 정보를 찾을 수 없으면 {@code null}
 * @param withdrawn 탈퇴했거나 사용자 정보를 찾을 수 없으면 {@code true}
 */
public record ScheduleUserProfileResult(String nickname, String profileImageUrl, Gender gender,
        ScheduleAgeGroupResult ageGroup, boolean withdrawn) {

    public ParticipantProfile toParticipantProfile() {
        return new ParticipantProfile(gender, ageGroup == null ? null : ageGroup.toAgeGroup());
    }
}
