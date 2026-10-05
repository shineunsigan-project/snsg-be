package com.shineunsigan.snsg.auth.application.dto.result;

/**
 * 소셜 인증 결과. 기존 회원이면 로그인까지 끝나고, 신규면 가입 토큰만 받는다.
 * 신규 사용자를 여기서 만들지 않는다. 성별·생년월일을 아직 받지 못했기 때문이다.
 */
public sealed interface SocialAuthResult {

    record LoggedIn(TokenResult tokens) implements SocialAuthResult {
    }

    record SignUpRequired(String signUpToken) implements SocialAuthResult {
    }
}
