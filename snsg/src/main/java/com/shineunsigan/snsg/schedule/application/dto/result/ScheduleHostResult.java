package com.shineunsigan.snsg.schedule.application.dto.result;

/**
 * 일정 상세의 개설자. 탈퇴한 개설자는 user가 가린 값 그대로다.
 *
 * @param withdrawn 탈퇴했거나 사용자 정보를 찾을 수 없으면 {@code true}
 */
public record ScheduleHostResult(String nickname, String profileImageUrl, boolean withdrawn) {

    public static ScheduleHostResult from(ScheduleUserProfileResult profile) {
        return new ScheduleHostResult(profile.nickname(), profile.profileImageUrl(), profile.withdrawn());
    }
}
