package com.shineunsigan.snsg.auth.application.port.out;

import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;

/**
 * 소셜 로그인 제공자에서 사용자 정보를 받아온다. 구현은 provider별 어댑터다.
 */
public interface SocialLoginPort {

    SocialProvider provider();

    /**
     * 사용자를 보낼 소셜 인증 페이지 주소.
     */
    String authorizeUrl();

    /**
     * 인가코드를 토큰으로 바꾸고 사용자 정보를 조회한다.
     */
    SocialUserResult fetchUser(String authorizationCode);
}
