package com.shineunsigan.snsg.auth.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.auth.application.port.out.SchedulePort;
import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 직접 요청한 탈퇴. 남은 일정으로 탈퇴할 수 있는지 schedule에 확인한 뒤 user를 탈퇴시킨다.
 * user가 schedule을 부르면 schedule → user와 순환이 되므로, 데이터가 없는 auth가 두 도메인을 조합한다.
 * <p>
 * 확인과 탈퇴 사이에 일정에 참여하는 경합은 허용한다. 세션 삭제·소셜 연동 해제는 user가 커밋 뒤에 내는 탈퇴 이벤트가 맡는다.
 * 소셜 연동 해제 통지로 오는 탈퇴({@link HandleSocialUnlinkFacade})는 남은 일정을 확인하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class WithdrawFacade {

    private final SchedulePort schedulePort;
    private final UserPort userPort;

    public void withdraw(UUID userId) {
        validateWithdrawable(userId);
        userPort.withdraw(userId);
    }

    private void validateWithdrawable(UUID userId) {
        if (!schedulePort.checkWithdrawable(userId).withdrawable()) {
            throw new BusinessException(AuthErrorCode.WITHDRAWAL_BLOCKED);
        }
    }
}
