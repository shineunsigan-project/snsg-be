package com.shineunsigan.snsg.auth.infrastructure.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.auth.application.dto.result.WithdrawalEligibilityResult;
import com.shineunsigan.snsg.auth.application.port.out.SchedulePort;
import com.shineunsigan.snsg.schedule.application.port.in.CheckWithdrawableUseCase;

import lombok.RequiredArgsConstructor;

/**
 * auth에서 schedule 도메인을 부르는 유일한 자리. 여기서만 schedule 패키지를 import한다.
 * 이름 앞의 Auth는 다른 도메인의 adapter와 빈 이름이 겹치지 않게 붙였다.
 */
@Component
@RequiredArgsConstructor
public class AuthScheduleAdapter implements SchedulePort {

    private final CheckWithdrawableUseCase checkWithdrawableUseCase;

    @Override
    public WithdrawalEligibilityResult checkWithdrawable(UUID userId) {
        com.shineunsigan.snsg.schedule.application.dto.result.WithdrawalEligibilityResult result =
                checkWithdrawableUseCase.checkWithdrawable(userId);
        return new WithdrawalEligibilityResult(result.withdrawable(), result.availableAt());
    }
}
