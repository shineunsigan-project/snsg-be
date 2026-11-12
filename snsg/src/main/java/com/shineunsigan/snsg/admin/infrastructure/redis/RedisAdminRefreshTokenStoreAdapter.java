package com.shineunsigan.snsg.admin.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.admin.application.AdminProperties;
import com.shineunsigan.snsg.admin.application.port.out.AdminRefreshTokenStorePort;

/**
 * 어드민 refresh token을 기기 세션마다 Redis에 보관한다. 사용자 세션과 키가 겹치지 않게 {@code admin-} 접두사를 쓴다.
 * <p>
 * {@code admin-refresh:{adminId}:{sessionId}}에 토큰 해시를, {@code admin-sessions:{adminId}}에 sessionId 집합을 둔다.
 * 원문이 아니라 해시를 저장한다. 세션 키의 TTL은 세션 만료까지 남은 시간이다(재발급해도 늘지 않는다).
 * 집합의 TTL은 저장할 때마다 refresh 수명 전체로 다시 건다. 남은 시간이 가장 긴 세션보다 먼저 사라지지 않게 하기 위해서다.
 */
@Component
public class RedisAdminRefreshTokenStoreAdapter implements AdminRefreshTokenStorePort {

    private static final String REFRESH_KEY_PREFIX = "admin-refresh:";
    private static final String SESSIONS_KEY_PREFIX = "admin-sessions:";

    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTokenValidity;

    public RedisAdminRefreshTokenStoreAdapter(StringRedisTemplate redisTemplate, AdminProperties adminProperties) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenValidity = adminProperties.refreshTokenValidity();
    }

    /**
     * 세 명령을 MULTI/EXEC로 묶는다. 따로 보내면 SADD 직후 실패할 때 새로 생긴 집합이 TTL 없이 남는다.
     */
    @Override
    public void save(UUID adminId, String sessionId, String refreshToken, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        String refreshKey = refreshKey(adminId, sessionId);
        String sessionsKey = sessionsKey(adminId);
        String hashed = hash(refreshToken);

        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> stringOperations = (RedisOperations<String, String>) operations;
                stringOperations.multi();
                stringOperations.opsForValue().set(refreshKey, hashed, remaining);
                stringOperations.opsForSet().add(sessionsKey, sessionId);
                stringOperations.expire(sessionsKey, refreshTokenValidity);
                return stringOperations.exec();
            }
        });
    }

    @Override
    public boolean matches(UUID adminId, String sessionId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(refreshKey(adminId, sessionId));
        return stored != null && stored.equals(hash(refreshToken));
    }

    /**
     * 둘을 묶지 않는다. 사이에서 실패해 집합에 id만 남아도 무해하고, 집합에는 TTL이 걸려 있다.
     */
    @Override
    public void delete(UUID adminId, String sessionId) {
        redisTemplate.delete(refreshKey(adminId, sessionId));
        redisTemplate.opsForSet().remove(sessionsKey(adminId), sessionId);
    }

    @Override
    public void deleteAll(UUID adminId) {
        String sessionsKey = sessionsKey(adminId);
        Set<String> sessionIds = redisTemplate.opsForSet().members(sessionsKey);

        List<String> keys = new ArrayList<>();
        if (sessionIds != null) {
            sessionIds.forEach(sessionId -> keys.add(refreshKey(adminId, sessionId)));
        }
        keys.add(sessionsKey);
        redisTemplate.delete(keys);
    }

    private String refreshKey(UUID adminId, String sessionId) {
        return REFRESH_KEY_PREFIX + adminId + ":" + sessionId;
    }

    private String sessionsKey(UUID adminId) {
        return SESSIONS_KEY_PREFIX + adminId;
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
