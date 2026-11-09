package com.shineunsigan.snsg.auth.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.auth.application.AuthProperties;
import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;

/**
 * refresh token을 기기 세션마다 Redis에 보관한다. 같은 세션에서 재발급하면 덮어쓴다.
 * <p>
 * {@code refresh:{userId}:{sessionId}}에 토큰 해시를, {@code sessions:{userId}}에 사용자의 sessionId 집합을 둔다.
 * 집합은 탈퇴 때 모든 세션을 지우기 위해 둔다.
 * <p>
 * 토큰 원문이 아니라 해시를 저장한다. Redis 덤프나 백업이 유출돼도 토큰을 복원할 수 없다.
 * 두 키 모두 TTL은 토큰 만료와 같은 값을 쓴다. 만료된 세션은 저절로 사라지므로 따로 정리하지 않는다.
 * 집합에 만료된 세션 id가 남아 있어도 지울 대상이 없을 뿐이라 무해하다.
 */
@Component
public class RedisRefreshTokenStoreAdapter implements RefreshTokenStorePort {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String SESSIONS_KEY_PREFIX = "sessions:";

    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTokenValidity;

    public RedisRefreshTokenStoreAdapter(StringRedisTemplate redisTemplate, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenValidity = authProperties.refreshTokenValidity();
    }

    /**
     * 세 명령을 MULTI/EXEC로 묶는다. 따로 보내면 SADD 직후 실패할 때 새로 생긴 집합이 TTL 없이 남는다.
     */
    @Override
    public void save(UUID userId, String sessionId, String refreshToken) {
        String refreshKey = refreshKey(userId, sessionId);
        String sessionsKey = sessionsKey(userId);
        String hashed = hash(refreshToken);

        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> stringOperations = (RedisOperations<String, String>) operations;
                stringOperations.multi();
                stringOperations.opsForValue().set(refreshKey, hashed, refreshTokenValidity);
                stringOperations.opsForSet().add(sessionsKey, sessionId);
                stringOperations.expire(sessionsKey, refreshTokenValidity);
                return stringOperations.exec();
            }
        });
    }

    @Override
    public boolean matches(UUID userId, String sessionId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(refreshKey(userId, sessionId));
        return stored != null && stored.equals(hash(refreshToken));
    }

    /**
     * 둘을 묶지 않는다. 사이에서 실패해 집합에 id만 남아도 무해하고, 집합에는 TTL이 걸려 있다.
     */
    @Override
    public void delete(UUID userId, String sessionId) {
        redisTemplate.delete(refreshKey(userId, sessionId));
        redisTemplate.opsForSet().remove(sessionsKey(userId), sessionId);
    }

    /**
     * 모든 세션 키와 집합을 DEL 한 번으로 지운다.
     */
    @Override
    public void deleteAll(UUID userId) {
        String sessionsKey = sessionsKey(userId);
        Set<String> sessionIds = redisTemplate.opsForSet().members(sessionsKey);

        List<String> keys = new ArrayList<>();
        if (sessionIds != null) {
            sessionIds.forEach(sessionId -> keys.add(refreshKey(userId, sessionId)));
        }
        keys.add(sessionsKey);
        redisTemplate.delete(keys);
    }

    private String refreshKey(UUID userId, String sessionId) {
        return REFRESH_KEY_PREFIX + userId + ":" + sessionId;
    }

    private String sessionsKey(UUID userId) {
        return SESSIONS_KEY_PREFIX + userId;
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
