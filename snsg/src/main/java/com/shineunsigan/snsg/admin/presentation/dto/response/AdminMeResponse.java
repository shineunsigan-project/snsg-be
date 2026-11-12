package com.shineunsigan.snsg.admin.presentation.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import com.shineunsigan.snsg.admin.application.dto.result.AdminResult;
import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminMeResponse(

        @Schema(description = "어드민 식별자", example = "0192f3a4-5b6c-7d8e-9f01-23456789abcd")
        UUID id,

        @Schema(description = "로그인 아이디", example = "mintadmin")
        String loginId,

        @Schema(description = "실명", example = "김민트")
        String name,

        @Schema(description = "소속팀. 값과 이름은 가입 요청의 team과 같다", example = "BACKEND")
        AdminTeam team,

        @Schema(description = "생년월일(yyyy-MM-dd)", example = "1996-04-12")
        LocalDate birthDate,

        @Schema(description = "역할. MASTER는 가입 승인 등 MASTER 전용 메뉴를 볼 수 있다. ADMIN은 일반 어드민이다",
                example = "ADMIN")
        AdminRole role) {

    public static AdminMeResponse from(AdminResult result) {
        return new AdminMeResponse(result.id(), result.loginId(), result.name(), result.team(), result.birthDate(),
                result.role());
    }
}
