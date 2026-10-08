package com.shineunsigan.snsg.auth.infrastructure.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.shineunsigan.snsg.auth.application.RevokeAuthUseCase;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.application.event.UserWithdrawnEvent;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;

import lombok.RequiredArgsConstructor;

/**
 * user 도메인의 탈퇴 이벤트를 받아 인증 흔적을 지운다.
 * <p>
 * 이벤트가 유실돼 refresh token이 남아도 재발급 시점에 사용자가 유효한지 다시 확인하므로 막힌다
 * ({@code ReissueTokenUseCase}).
 */
@Component
@RequiredArgsConstructor
public class UserWithdrawnEventListener {

    private final RevokeAuthUseCase revokeAuthUseCase;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserWithdrawnEvent event) {
        revokeAuthUseCase.revoke(event.userId(), toAuthProvider(event.provider()), event.providerUserId(),
                event.reason() == WithdrawReason.USER_REQUESTED);
    }

    private SocialProvider toAuthProvider(com.shineunsigan.snsg.user.domain.constants.SocialProvider provider) {
        return switch (provider) {
            case KAKAO -> SocialProvider.KAKAO;
            case NAVER -> SocialProvider.NAVER;
            case APPLE -> SocialProvider.APPLE;
        };
    }
}
