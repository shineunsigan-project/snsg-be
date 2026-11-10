package com.shineunsigan.snsg.schedule.application.dto.result;

import java.time.LocalDateTime;

/**
 * @param withdrawable 지금 탈퇴할 수 있는지
 * @param availableAt  탈퇴할 수 있게 되는 시각. 막는 일정 중 가장 늦은 시작 + 탈퇴 제한 시간이다. 탈퇴할 수 있으면 {@code null}
 */
public record WithdrawalEligibilityResult(boolean withdrawable, LocalDateTime availableAt) {

    public static WithdrawalEligibilityResult allowed() {
        return new WithdrawalEligibilityResult(true, null);
    }

    public static WithdrawalEligibilityResult blockedUntil(LocalDateTime availableAt) {
        return new WithdrawalEligibilityResult(false, availableAt);
    }
}
