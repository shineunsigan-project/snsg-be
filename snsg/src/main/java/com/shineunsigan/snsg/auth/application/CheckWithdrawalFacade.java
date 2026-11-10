package com.shineunsigan.snsg.auth.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.auth.application.dto.result.WithdrawalEligibilityResult;
import com.shineunsigan.snsg.auth.application.port.out.SchedulePort;

import lombok.RequiredArgsConstructor;

/**
 * 탈퇴 가능 여부를 알려준다. 탈퇴 화면이 불가 안내와 재확인 중 무엇을 띄울지 정하는 데 쓴다.
 * 탈퇴는 user의 일이지만 확인할 데이터는 schedule에 있어, 데이터가 없는 auth가 흐름을 맡는다.
 */
@Service
@RequiredArgsConstructor
public class CheckWithdrawalFacade {

    private final SchedulePort schedulePort;

    public WithdrawalEligibilityResult check(UUID userId) {
        return schedulePort.checkWithdrawable(userId);
    }
}
