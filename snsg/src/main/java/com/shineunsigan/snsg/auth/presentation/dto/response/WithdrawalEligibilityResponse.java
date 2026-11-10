package com.shineunsigan.snsg.auth.presentation.dto.response;

import java.time.LocalDateTime;

import com.shineunsigan.snsg.auth.application.dto.result.WithdrawalEligibilityResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record WithdrawalEligibilityResponse(

        @Schema(description = "지금 탈퇴할 수 있는지. true면 탈퇴 재확인 팝업을, false면 탈퇴 불가 팝업을 띄운다", example = "false")
        boolean withdrawable,

        @Schema(description = """
                탈퇴할 수 있게 되는 시각. 한국 시간 ISO-8601(오프셋 없음).
                탈퇴를 막는 일정 중 시작 시각이 가장 늦은 일정의 시작 + 24시간이다. 탈퇴 불가 팝업에 안내할 때 쓴다.
                withdrawable이 true면 null이다""",
                example = "2026-11-15T19:00:00", nullable = true)
        LocalDateTime availableAt) {

    public static WithdrawalEligibilityResponse from(WithdrawalEligibilityResult result) {
        return new WithdrawalEligibilityResponse(result.withdrawable(), result.availableAt());
    }
}
