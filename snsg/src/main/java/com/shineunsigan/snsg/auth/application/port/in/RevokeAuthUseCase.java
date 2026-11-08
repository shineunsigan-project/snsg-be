package com.shineunsigan.snsg.auth.application.port.in;

import java.util.UUID;

import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;

/**
 * 타 도메인의 탈퇴를 받아 인증 흔적을 지우는 입구. user의 탈퇴 이벤트를 auth 리스너가 받아 호출한다.
 */
public interface RevokeAuthUseCase {

    void revoke(UUID userId, SocialProvider provider, String providerUserId, boolean unlinkSocial);
}
