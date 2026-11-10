package com.shineunsigan.snsg.schedule.presentation.dto.response;

import com.shineunsigan.snsg.schedule.application.dto.result.ParticipantListItemResult;
import com.shineunsigan.snsg.schedule.domain.constants.Gender;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ScheduleParticipant", description = "참여자 명단의 한 사람. 이름·이메일·생년월일·사용자 ID는 주지 않는다")
public record ScheduleParticipantResponse(

        @Schema(description = "닉네임. 탈퇴했으면 \"탈퇴한 사용자\"", example = "민트초코")
        String nickname,

        @Schema(description = "프로필 사진 경로. 도메인 없이 경로만 온다. 탈퇴했으면 기본 이미지 경로",
                example = "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png")
        String profileImageUrl,

        @Schema(description = "성별. MALE 남자, FEMALE 여자. 탈퇴한 사람도 저장된 성별이 온다. 사용자 정보를 찾을 수 없으면 null",
                example = "FEMALE", nullable = true)
        Gender gender,

        @Schema(description = "연령대. 탈퇴했거나 사용자 정보를 찾을 수 없으면 null", nullable = true)
        ScheduleDetailResponse.AgeGroup ageGroup,

        @Schema(description = "개설자면 true. 명단에서 개설자는 항상 맨 앞 한 명이다", example = "true")
        boolean isHost,

        @Schema(description = "보는 사람 본인이면 true. 개설자가 보면 개설자 항목이 isHost·isMe 모두 true다", example = "false")
        boolean isMe,

        @Schema(description = "탈퇴했거나 사용자 정보를 찾을 수 없으면 true. 화면에 탈퇴 표시를 붙일 때 쓴다", example = "false")
        boolean withdrawn) {

    public static ScheduleParticipantResponse from(ParticipantListItemResult result) {
        return new ScheduleParticipantResponse(result.nickname(), result.profileImageUrl(), result.gender(),
                ScheduleDetailResponse.AgeGroup.from(result.ageGroup()), result.isHost(), result.isMe(),
                result.withdrawn());
    }
}
