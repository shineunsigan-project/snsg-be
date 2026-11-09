package com.shineunsigan.snsg.auth.application.port.out;

import java.util.UUID;

/**
 * refresh token 보관. 기기(세션)마다 하나씩 유효하다.
 * 구현은 infrastructure의 Redis 어댑터다.
 */
public interface RefreshTokenStorePort {

    /**
     * 세션의 refresh token을 저장한다. 같은 세션에 이미 있으면 덮어써 이전 토큰은 무효가 된다.
     */
    void save(UUID userId, String sessionId, String refreshToken);

    boolean matches(UUID userId, String sessionId, String refreshToken);

    /**
     * 한 세션만 지운다. 로그아웃용이다. 다른 기기의 세션은 그대로 둔다.
     */
    void delete(UUID userId, String sessionId);

    /**
     * 사용자의 모든 세션을 지운다. 탈퇴용이다.
     */
    void deleteAll(UUID userId);
}
