package com.shineunsigan.snsg.auth.application.dto.result;

import java.time.LocalDateTime;

/**
 * @param withdrawable 지금 탈퇴할 수 있는지
 * @param availableAt  탈퇴할 수 있게 되는 시각. 탈퇴할 수 있으면 {@code null}
 */
public record WithdrawalEligibilityResult(boolean withdrawable, LocalDateTime availableAt) {
}
