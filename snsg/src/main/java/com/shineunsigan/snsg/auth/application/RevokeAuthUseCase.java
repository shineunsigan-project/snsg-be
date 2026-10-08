package com.shineunsigan.snsg.auth.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.application.port.out.SocialUnlinkPort;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 탈퇴한 사용자의 인증 흔적을 지운다.
 * <p>
 * 소셜 연동 해제는 사용자가 우리 서비스에서 탈퇴했을 때만 한다. 소셜 쪽에서 끊어 통지가 온 경우는
 * 이미 끊긴 상태다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevokeAuthUseCase {

    private final RefreshTokenStorePort refreshTokenStorePort;
    private final SocialUnlinkPort socialUnlinkPort;

    public void revoke(UUID userId, SocialProvider provider, String providerUserId, boolean unlinkSocial) {
        refreshTokenStorePort.delete(userId);

        if (!unlinkSocial) {
            return;
        }
        try {
            socialUnlinkPort.unlink(provider, providerUserId);
        } catch (RuntimeException exception) {
            // 연동 해제에 실패해도 탈퇴는 이미 끝났다. 사용자가 소셜 설정에서 직접 끊을 수 있다.
            log.warn("소셜 연동을 끊지 못했다. provider={}, providerUserId={}", provider, providerUserId, exception);
        }
    }
}
