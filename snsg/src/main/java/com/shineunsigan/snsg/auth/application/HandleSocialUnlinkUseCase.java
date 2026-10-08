package com.shineunsigan.snsg.auth.application;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 소셜 쪽에서 연동이 끊겼다는 통지를 처리한다. 사용자를 탈퇴시킨다.
 * <p>
 * 이 경로는 인증 없이 열려 있어 발신자를 반드시 확인해야 한다. 확인하지 않으면
 * 누구나 호출해 남의 계정을 탈퇴시킬 수 있다.
 */
@Service
@RequiredArgsConstructor
public class HandleSocialUnlinkUseCase {

    private static final String ADMIN_KEY_PREFIX = "KakaoAK ";

    private final UserPort userPort;
    private final KakaoWebhookProperties kakaoWebhookProperties;

    public void handleKakaoUnlink(String authorization, String providerUserId) {
        verifyKakao(authorization);
        userPort.withdrawBySocialAccount(SocialProvider.KAKAO, providerUserId);
    }

    private void verifyKakao(String authorization) {
        if (authorization == null || !authorization.startsWith(ADMIN_KEY_PREFIX)) {
            throw new BusinessException(AuthErrorCode.INVALID_WEBHOOK_SENDER);
        }
        String received = authorization.substring(ADMIN_KEY_PREFIX.length());
        // 길이 차이로 키를 한 글자씩 맞춰갈 수 없도록 상수 시간으로 비교한다.
        if (!MessageDigest.isEqual(received.getBytes(StandardCharsets.UTF_8),
                kakaoWebhookProperties.adminKey().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(AuthErrorCode.INVALID_WEBHOOK_SENDER);
        }
    }
}
