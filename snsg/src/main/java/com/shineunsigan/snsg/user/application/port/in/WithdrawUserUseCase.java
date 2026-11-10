package com.shineunsigan.snsg.user.application.port.in;

import java.util.UUID;

import com.shineunsigan.snsg.user.domain.constants.SocialProvider;

/**
 * 타 도메인이 탈퇴를 요청하는 입구. 탈퇴 흐름을 조합하는 auth가 호출한다.
 * 사용자가 직접 요청한 탈퇴와, 소셜 쪽에서 연동을 끊었다는 통지로 오는 탈퇴 두 가지다.
 */
public interface WithdrawUserUseCase {

    /**
     * 사용자가 직접 요청한 탈퇴. 탈퇴 가능 여부(남은 일정)는 호출하는 쪽이 먼저 확인한다.
     * 없거나 이미 탈퇴한 사용자면 USER_NOT_FOUND다.
     */
    void withdraw(UUID userId);

    /**
     * 이미 탈퇴했거나 없는 계정이면 아무 일도 하지 않는다.
     * 소셜이 보내는 통지는 가입하지 않은 사용자에 대해서도 오기 때문이다.
     */
    void withdrawBySocialAccount(SocialProvider provider, String providerUserId);
}
