package com.shineunsigan.snsg.user.application.port.in;

import com.shineunsigan.snsg.user.domain.constants.SocialProvider;

/**
 * 타 도메인이 탈퇴를 요청하는 입구. 소셜 쪽에서 연동을 끊었다는 통지를 auth가 받아 호출한다.
 */
public interface WithdrawUserUseCase {

    /**
     * 이미 탈퇴했거나 없는 계정이면 아무 일도 하지 않는다.
     * 소셜이 보내는 통지는 가입하지 않은 사용자에 대해서도 오기 때문이다.
     */
    void withdrawBySocialAccount(SocialProvider provider, String providerUserId);
}
