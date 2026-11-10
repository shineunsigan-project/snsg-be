package com.shineunsigan.snsg.auth.application.port.out;

import java.util.UUID;

import com.shineunsigan.snsg.auth.application.dto.result.WithdrawalEligibilityResult;

/**
 * schedule 도메인을 부르는 통로. 구현(어댑터)만 schedule 패키지를 안다.
 */
public interface SchedulePort {

    /**
     * 남은 일정 때문에 탈퇴할 수 없는지와 탈퇴할 수 있게 되는 시각. 판정과 계산은 schedule이 한다.
     */
    WithdrawalEligibilityResult checkWithdrawable(UUID userId);
}
