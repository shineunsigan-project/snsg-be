package com.shineunsigan.snsg.auth.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.infrastructure.jwt.JwtProperties;

/**
 * refresh token을 Redis에 보관한다. 사용자당 하나만 유효하고, 재발급하면 덮어쓴다.
 * <p>
 * 토큰 원문이 아니라 해시를 저장한다. Redis 덤프나 백업이 유출돼도 토큰을 복원할 수 없다.
 * TTL은 토큰 만료와 같은 값을 쓴다. 만료된 토큰은 저절로 사라지므로 따로 정리하지 않는다.
 */
@Component
public class RedisRefreshTokenStoreAdapter implements RefreshTokenStorePort {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTokenValidity;

    public RedisRefreshTokenStoreAdapter(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenValidity = jwtProperties.refreshTokenValidity();
    }

    @Override
    public void save(UUID userId, String refreshToken) {
        redisTemplate.opsForValue().set(key(userId), hash(refreshToken), refreshTokenValidity);
    }

    @Override
    public boolean matches(UUID userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(key(userId));
        return stored != null && stored.equals(hash(refreshToken));
    }

    @Override
    public void delete(UUID userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }

    private String hash(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", exception);
        }
    }
}
