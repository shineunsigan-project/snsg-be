package com.shineunsigan.snsg.admin.presentation;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 어드민 refresh token 쿠키를 만든다. 사용자 쿠키({@code refreshToken})와 이름·경로가 다르다.
 * <p>
 * HttpOnly라 자바스크립트가 읽을 수 없다. Path를 {@code /api/admin/v1/auth}로 좁혀 다른 어드민 API 호출마다
 * 쿠키가 따라붙지 않게 한다.
 * <p>
 * 수명은 세션 만료까지 남은 시간이다. 세션은 로그인 후 12시간에 끝나고 재발급해도 늘지 않으므로 쿠키도 그때 사라진다.
 */
@Component
public class AdminCookieFactory {

    public static final String REFRESH_TOKEN = "adminRefreshToken";

    private static final String PATH = "/api/admin/v1/auth";

    /**
     * 남은 시간을 초 단위로 올림한다. 로그인 직후에는 refresh 수명(초)과 같고, 만료를 1초 넘게 넘기지 않는다.
     */
    public ResponseCookie refreshToken(String refreshToken, Instant expiresAt) {
        long remainingMillis = Duration.between(Instant.now(), expiresAt).toMillis();
        long maxAgeSeconds = Math.max(0, (remainingMillis + 999) / 1000);
        return build(refreshToken, Duration.ofSeconds(maxAgeSeconds));
    }

    /**
     * 로그아웃 시 쿠키를 지운다. 같은 이름·경로로 수명 0짜리를 덮어쓴다.
     */
    public ResponseCookie expiredRefreshToken() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
