package com.shineunsigan.snsg.user.presentation.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.shineunsigan.snsg.user.application.dto.result.MyProfileResult;
import com.shineunsigan.snsg.user.domain.constants.AgePhase;
import com.shineunsigan.snsg.user.domain.constants.Gender;

import io.swagger.v3.oas.annotations.media.Schema;

public record MyProfileResponse(

        @Schema(description = "사용자 ID", example = "01a19cc6-3d80-7000-8000-000000000001")
        UUID id,

        @Schema(description = "실명. 바꿀 수 없다", example = "김민트")
        String name,

        @Schema(description = "닉네임", example = "민트초코")
        String nickname,

        @Schema(description = "성별. FEMALE 여성, MALE 남성. 바꿀 수 없으므로 읽기 전용으로 보여준다", example = "FEMALE")
        Gender gender,

        @Schema(description = "생년월일(yyyy-MM-dd). 바꿀 수 없다", example = "1996-04-12")
        LocalDate birthDate,

        @Schema(description = "이메일. 등록하지 않았으면 빈 문자열이다(null이 아니다)", example = "mint@example.com")
        String email,

        @Schema(description = "프로필 사진 경로. 도메인 없이 경로만 온다. 사진을 올리지 않았으면 소셜 사진 주소나 기본 이미지 경로다",
                example = "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png")
        String profileImageUrl,

        @Schema(description = "연령대. 오늘(한국 시간) 기준 만 나이로 계산한다")
        AgeGroupResponse ageGroup,

        @Schema(description = """
                닉네임을 다시 바꿀 수 있는 시각. 한국 시간 ISO-8601(오프셋 없음).
                마지막 닉네임 변경 후 14일이 지나지 않았으면 그 시각이고, 지금 바꿀 수 있으면(바꾼 적이 없을 때 포함) null이다.
                값이 있으면 닉네임 칸을 막고 이 날짜를 안내한다""",
                example = "2026-11-23T14:30:00", nullable = true)
        LocalDateTime nicknameChangeableAt) {

    public static MyProfileResponse from(MyProfileResult result) {
        return new MyProfileResponse(result.id(), result.name(), result.nickname(), result.gender(),
                result.birthDate(), result.email(), result.profileImageUrl(),
                new AgeGroupResponse(result.ageDecade(), result.agePhase()), result.nicknameChangeableAt());
    }

    public record AgeGroupResponse(

            @Schema(description = "십의 자리 × 10. 20이면 20대다. 만 18·19세는 10이다", example = "20")
            int decade,

            @Schema(description = "EARLY 초반(일의 자리 0~3), MID 중반(4~6), LATE 후반(7~9). 18·19세는 10대 후반(LATE)이다",
                    example = "LATE")
            AgePhase phase) {
    }
}
