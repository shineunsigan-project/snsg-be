package com.shineunsigan.snsg.schedule.application.dto.result;

import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.constants.Gender;

/**
 * 참여자 명단의 한 사람. 탈퇴했거나 사용자 정보를 찾을 수 없으면 user가 가린 값 그대로다.
 *
 * @param gender   사용자 정보를 찾을 수 없으면 {@code null}
 * @param ageGroup 탈퇴자·사용자 정보를 찾을 수 없으면 {@code null}
 * @param isMe     보는 사람 본인이면 {@code true}
 */
public record ParticipantListItemResult(String nickname, String profileImageUrl, Gender gender,
        ScheduleAgeGroupResult ageGroup, boolean isHost, boolean isMe, boolean withdrawn) {

    public static ParticipantListItemResult of(ScheduleParticipantQueryResult participant,
            ScheduleUserProfileResult profile, UUID viewerId) {
        return new ParticipantListItemResult(profile.nickname(), profile.profileImageUrl(), profile.gender(),
                profile.ageGroup(), participant.isHost(), participant.userId().equals(viewerId), profile.withdrawn());
    }
}
