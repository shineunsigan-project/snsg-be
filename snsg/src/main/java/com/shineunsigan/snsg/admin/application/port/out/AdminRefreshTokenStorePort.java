package com.shineunsigan.snsg.admin.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * 어드민 refresh token 보관. 기기(세션)마다 하나씩 유효하다. 구현은 infrastructure의 Redis 어댑터다.
 */
public interface AdminRefreshTokenStorePort {

    /**
     * 세션의 refresh token을 {@code expiresAt}(세션 만료)까지 저장한다. 같은 세션에 이미 있으면 덮어써 이전 토큰은 무효가 된다.
     */
    void save(UUID adminId, String sessionId, String refreshToken, Instant expiresAt);

    boolean matches(UUID adminId, String sessionId, String refreshToken);

    /**
     * 한 세션만 지운다. 로그아웃용이다. 다른 기기의 세션은 그대로 둔다.
     */
    void delete(UUID adminId, String sessionId);

    /**
     * 어드민의 모든 세션을 지운다. 승인 상태가 아닌 어드민의 재발급을 막을 때 쓴다.
     */
    void deleteAll(UUID adminId);
}
