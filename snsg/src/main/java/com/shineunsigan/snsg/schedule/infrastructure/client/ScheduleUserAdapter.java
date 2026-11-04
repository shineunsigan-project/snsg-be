package com.shineunsigan.snsg.schedule.infrastructure.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.schedule.application.port.out.UserPort;
import com.shineunsigan.snsg.user.application.port.in.FindUserUseCase;

import lombok.RequiredArgsConstructor;

/**
 * schedule에서 user 도메인을 부르는 자리. 여기서만 user 패키지를 import한다.
 * 이름을 auth의 UserAdapter와 다르게 둔다. 빈 이름이 같으면 기동이 실패한다.
 */
@Component
@RequiredArgsConstructor
public class ScheduleUserAdapter implements UserPort {

    private final FindUserUseCase findUserUseCase;

    /** 탈퇴하면 사용자가 삭제 상태가 되어 찾히지 않는다. */
    @Override
    public boolean isActive(UUID userId) {
        return findUserUseCase.exists(userId);
    }
}
