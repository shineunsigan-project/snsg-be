package com.shineunsigan.snsg.auth.presentation;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.auth.infrastructure.jwt.JwtProperties;

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

    private static final String PATH = "/api/v1/auth";
    private static final Duration SIGN_UP_TOKEN_MAX_AGE = Duration.ofMinutes(30);

    private final Duration refreshTokenMaxAge;

    public AuthCookieFactory(JwtProperties jwtProperties) {
        this.refreshTokenMaxAge = jwtProperties.refreshTokenValidity();
    }

    public ResponseCookie refreshToken(String refreshToken) {
        return build(REFRESH_TOKEN, refreshToken, refreshTokenMaxAge);
    }

    public ResponseCookie signUpToken(String signUpToken) {
        return build(SIGN_UP_TOKEN, signUpToken, SIGN_UP_TOKEN_MAX_AGE);
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
}
