package com.shineunsigan.snsg.auth.application.port.out;

import java.util.UUID;

/**
 * 토큰 발급과 검증. 구현은 infrastructure의 JWT 어댑터다.
 */
public interface TokenPort {

    String issueAccessToken(UUID userId);

    String issueRefreshToken(UUID userId);

    /**
     * 유효한 토큰이면 사용자 식별자를 돌려준다. 만료·위조면 예외를 던진다.
     */
    UUID parseUserId(String token);
}
