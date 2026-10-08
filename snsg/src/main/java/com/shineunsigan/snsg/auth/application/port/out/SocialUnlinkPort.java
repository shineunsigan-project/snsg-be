package com.shineunsigan.snsg.auth.application.port.out;

import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;

/**
 * 소셜 쪽에 걸린 우리 앱 연동을 끊는다.
 */
public interface SocialUnlinkPort {

    void unlink(SocialProvider provider, String providerUserId);
}
