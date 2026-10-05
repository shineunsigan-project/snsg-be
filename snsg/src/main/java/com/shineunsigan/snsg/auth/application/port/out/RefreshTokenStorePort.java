package com.shineunsigan.snsg.auth.application.port.out;

import java.util.UUID;

/**
 * refresh token 보관. 사용자당 하나만 유효하다.
 * 구현은 infrastructure의 Redis 어댑터다.
 */
public interface RefreshTokenStorePort {

    void save(UUID userId, String refreshToken);

    boolean matches(UUID userId, String refreshToken);

    void delete(UUID userId);
}
