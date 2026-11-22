package com.shineunsigan.snsg.auth.presentation;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.auth.application.AuthProperties;

/**
 * 토큰을 담는 쿠키를 만든다. 무엇을 쿠키로 주고 무엇을 본문으로 줄지는 presentation의 결정이다.
 * <p>
 * 모두 HttpOnly다. 자바스크립트가 읽을 수 있으면 XSS 한 번에 토큰이 털린다.
 * Path를 {@code /api/v1/auth}로 좁혀 일정·알림 API 호출마다 쿠키가 따라붙지 않게 한다.
 */
@Component
public class AuthCookieFactory {

    public static final String REFRESH_TOKEN = "refreshToken";
    public static final String SIGN_UP_TOKEN = "signUpToken";
    public static final String OAUTH_STATE = "oauthState";

    private static final String PATH = "/api/v1/auth";
    /** 카카오 인가코드 유효시간(10분)에 맞춘다. 그 뒤의 콜백은 어차피 실패한다. */
    private static final Duration OAUTH_STATE_MAX_AGE = Duration.ofMinutes(10);

    private final Duration refreshTokenMaxAge;
    private final Duration signUpTokenMaxAge;

    public AuthCookieFactory(AuthProperties authProperties) {
        this.refreshTokenMaxAge = authProperties.refreshTokenValidity();
        this.signUpTokenMaxAge = authProperties.signupSessionTtl();
    }

    public ResponseCookie refreshToken(String refreshToken) {
        return build(REFRESH_TOKEN, refreshToken, refreshTokenMaxAge);
    }

    public ResponseCookie signUpToken(String signUpToken) {
        return build(SIGN_UP_TOKEN, signUpToken, signUpTokenMaxAge);
    }

    /**
     * 소셜 로그인을 시작한 브라우저에 state를 묶는다(로그인 CSRF 방지).
     * <p>
     * 토큰 쿠키와 달리 SameSite=Lax다. 콜백은 소셜이 보내는 최상위 GET 이동이라 Lax 쿠키가 따라온다.
     */
    public ResponseCookie oauthState(String state) {
        return oauthStateCookie(state, OAUTH_STATE_MAX_AGE);
    }

    /**
     * 콜백 성공 시 state 쿠키를 지운다. 같은 이름·경로·SameSite로 수명 0짜리를 덮어쓴다.
     */
    public ResponseCookie expiredOAuthState() {
        return oauthStateCookie("", Duration.ZERO);
    }

    /**
     * 로그아웃·가입 완료 시 쿠키를 지운다. 같은 이름·경로로 수명 0짜리를 덮어쓴다.
     */
    public ResponseCookie expired(String name) {
        return build(name, "", Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie oauthStateCookie(String value, Duration maxAge) {
        return ResponseCookie.from(OAUTH_STATE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
