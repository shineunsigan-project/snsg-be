package com.shineunsigan.snsg.schedule.application.port.in;

import java.util.UUID;

import com.shineunsigan.snsg.schedule.application.dto.result.WithdrawalEligibilityResult;

/**
 * 타 도메인이 탈퇴 가능 여부를 묻는 입구. 탈퇴 흐름을 조합하는 auth가 user 탈퇴 전에 호출한다.
 */
public interface CheckWithdrawableUseCase {

    /**
     * 확정 참여(개설 포함)한 취소되지 않은 일정 중 시작 + 탈퇴 제한 시간이 지나지 않은 것이 하나라도 있으면 탈퇴할 수 없다.
     * 탈퇴한 사용자도 계산한다. 사용자가 있는지는 보지 않는다.
     */
    WithdrawalEligibilityResult checkWithdrawable(UUID userId);
}
