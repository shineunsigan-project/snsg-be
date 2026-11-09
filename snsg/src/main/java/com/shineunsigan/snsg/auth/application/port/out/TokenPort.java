package com.shineunsigan.snsg.auth.application.port.out;

import java.util.UUID;

import com.shineunsigan.snsg.auth.application.dto.result.RefreshTokenClaims;

/**
 * 토큰 발급과 검증. 구현은 infrastructure의 JWT 어댑터다.
 * <p>
 * access token은 사용자 식별자만 담는다. refresh token은 기기 세션 식별자(sessionId)를 함께 담는다.
 */
public interface TokenPort {

    String issueAccessToken(UUID userId);

    String issueRefreshToken(UUID userId, String sessionId);

    /**
     * 유효한 access token이면 사용자 식별자를 돌려준다. 만료·위조이거나 refresh token이면 예외를 던진다.
     */
    UUID parseUserId(String token);

    /**
     * 유효한 refresh token이면 사용자 식별자와 sessionId를 돌려준다.
     * 만료·위조이거나 sessionId가 없으면(access token, 이전 형식의 refresh token) 예외를 던진다.
     */
    RefreshTokenClaims parseRefreshToken(String token);
}
