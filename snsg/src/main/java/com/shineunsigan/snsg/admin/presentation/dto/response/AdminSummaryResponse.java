package com.shineunsigan.snsg.admin.presentation.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.shineunsigan.snsg.admin.application.dto.result.AdminSummaryResult;
import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminSummaryResponse(

        @Schema(description = "어드민 식별자. 승인·거절·이용 중지·해제 경로의 adminId로 쓴다",
                example = "0192f3a4-5b6c-7d8e-9f01-23456789abcd")
        UUID id,

        @Schema(description = "로그인 아이디", example = "mintadmin")
        String loginId,

        @Schema(description = "실명", example = "김민트")
        String name,

        @Schema(description = "소속팀. 값과 이름은 가입 요청의 team과 같다", example = "BACKEND")
        AdminTeam team,

        @Schema(description = "생년월일(yyyy-MM-dd)", example = "1996-04-12")
        LocalDate birthDate,

        @Schema(description = """
                역할. MASTER는 DB로만 만들고 관리하므로 승인·거절·이용 중지·해제 버튼을 그리지 않는다(부르면 403 MASTER_NOT_MODIFIABLE).
                ADMIN은 가입 신청으로 생긴 일반 어드민이다""", example = "ADMIN")
        AdminRole role,

        @Schema(description = """
                상태. PENDING(승인 대기): [승인]·[거절]을 그린다.
                APPROVED(승인됨): [이용 중지]를 그린다.
                REJECTED(거절됨): 버튼 없음. 거절은 되돌릴 수 없고 이 아이디는 다시 쓸 수 없다.
                SUSPENDED(이용 중지): [이용 중지 해제]를 그린다""", example = "PENDING")
        AdminStatus status,

        @Schema(description = "가입 신청 일시. 한국 시간, 오프셋 없는 ISO-8601", example = "2026-11-13T10:15:30")
        LocalDateTime createdAt) {

    public static AdminSummaryResponse from(AdminSummaryResult result) {
        return new AdminSummaryResponse(result.id(), result.loginId(), result.name(), result.team(),
                result.birthDate(), result.role(), result.status(), result.createdAt());
    }
}
