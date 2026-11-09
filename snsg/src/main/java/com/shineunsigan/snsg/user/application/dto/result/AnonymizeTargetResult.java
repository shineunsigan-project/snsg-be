package com.shineunsigan.snsg.user.application.dto.result;

import java.time.LocalDateTime;
import java.util.UUID;

import com.shineunsigan.snsg.user.domain.model.UserWithdrawal;

/**
 * 비식별 대상. 다음 페이지를 읽을 커서로도 쓴다.
 */
public record AnonymizeTargetResult(UUID userId, LocalDateTime anonymizeDueAt) {

    public static AnonymizeTargetResult from(UserWithdrawal withdrawal) {
        return new AnonymizeTargetResult(withdrawal.getUser().getId(), withdrawal.getAnonymizeDueAt());
    }
}
