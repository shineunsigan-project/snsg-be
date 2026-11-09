package com.shineunsigan.snsg.schedule.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 일정에 참여한다. 사용자(user)가 활성인지 확인한 뒤 참여는 {@link ScheduleCommandService}에 맡긴다.
 * <p>
 * 트랜잭션을 걸지 않는다. 타 도메인 호출을 트랜잭션 밖에 두기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class JoinScheduleFacade {

    private final UserPort userPort;
    private final ScheduleCommandService scheduleCommandService;

    public void join(UUID scheduleId, UUID userId) {
        validateActiveUser(userId);
        scheduleCommandService.join(scheduleId, userId);
    }

    private void validateActiveUser(UUID userId) {
        if (!userPort.isActive(userId)) {
            throw new BusinessException(ScheduleErrorCode.USER_NOT_ACTIVE);
        }
    }
}
