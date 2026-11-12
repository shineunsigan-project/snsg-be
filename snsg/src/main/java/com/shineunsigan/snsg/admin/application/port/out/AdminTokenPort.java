package com.shineunsigan.snsg.admin.application.port.out;

import java.time.Instant;
import java.util.UUID;

import com.shineunsigan.snsg.admin.application.dto.result.AdminRefreshTokenClaims;

/**
 * 어드민 토큰 발급과 검증. 사용자 토큰과 다른 키로 서명한다. 구현은 infrastructure의 JWT 어댑터다.
 * <p>
 * access token은 어드민 식별자만 담는다. 역할·상태는 담지 않고 요청마다 DB에서 읽는다.
 * refresh token은 기기 세션 식별자(sessionId)를 함께 담는다.
 * 만료 시각은 호출하는 쪽이 정해 넘긴다. 세션 만료(로그인 후 12시간)를 재발급이 이어받기 때문이다.
 */
public interface AdminTokenPort {

    String issueAccessToken(UUID adminId, Instant expiresAt);

    String issueRefreshToken(UUID adminId, String sessionId, Instant expiresAt);

    /**
     * 유효한 어드민 access token이면 어드민 식별자를 돌려준다. 만료·위조·다른 키(사용자 토큰)이거나
     * refresh token이면 예외를 던진다.
     */
    UUID parseAdminId(String token);

    /**
     * 유효한 어드민 refresh token이면 어드민 식별자, sessionId, 만료 시각(세션 만료)을 돌려준다.
     * 만료·위조·다른 키(사용자 토큰)이거나 sessionId가 없으면(access token) 예외를 던진다.
     */
    AdminRefreshTokenClaims parseRefreshToken(String token);
}
