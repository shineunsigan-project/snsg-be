package com.shineunsigan.snsg.admin.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.shineunsigan.snsg.admin.application.AdminProperties;
import com.shineunsigan.snsg.admin.application.port.out.AdminRefreshTokenStorePort;

/**
 * 실제 Redis에 붙어 어드민 세션 키 구조, 저장 형태(해시)와 TTL을 본다. 사용자 세션 키와 겹치지 않아야 한다.
 * 공유 Redis라 고정 어드민·세션을 쓰고, 만든 키는 이름으로 지운다.
 */
@SpringBootTest
class RedisAdminRefreshTokenStoreAdapterTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ae");
    private static final String SESSION_A = "session-a";
    private static final String SESSION_B = "session-b";
    private static final String TOKEN_A = "admin-refresh-token-a";
    private static final String TOKEN_B = "admin-refresh-token-b";

    private static final String REFRESH_KEY_A = "admin-refresh:" + ADMIN_ID + ":" + SESSION_A;
    private static final String REFRESH_KEY_B = "admin-refresh:" + ADMIN_ID + ":" + SESSION_B;
    private static final String SESSIONS_KEY = "admin-sessions:" + ADMIN_ID;
    private static final String USER_REFRESH_KEY_A = "refresh:" + ADMIN_ID + ":" + SESSION_A;
    private static final String USER_SESSIONS_KEY = "sessions:" + ADMIN_ID;

    @Autowired
    private AdminRefreshTokenStorePort store;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AdminProperties adminProperties;

    @AfterEach
    void clear() {
        redisTemplate.delete(List.of(REFRESH_KEY_A, REFRESH_KEY_B, SESSIONS_KEY));
    }

    @Test
    @DisplayName("원문이 아니라 해시를 저장하고, 세션 키 TTL은 세션 만료까지 남은 시간, 집합 TTL은 refresh 수명 이하다")
    void storesHashWithTtl() {
        store.save(ADMIN_ID, SESSION_A, TOKEN_A, sessionExpiresIn(Duration.ofHours(12)));

        String stored = redisTemplate.opsForValue().get(REFRESH_KEY_A);
        assertThat(stored).isNotNull().isNotEqualTo(TOKEN_A).matches("[0-9a-f]{64}");
        long validitySeconds = adminProperties.refreshTokenValidity().toSeconds();
        assertThat(redisTemplate.getExpire(REFRESH_KEY_A, TimeUnit.SECONDS)).isPositive()
                .isLessThanOrEqualTo(validitySeconds);
        assertThat(redisTemplate.getExpire(SESSIONS_KEY, TimeUnit.SECONDS)).isPositive()
                .isLessThanOrEqualTo(validitySeconds);
    }

    @Test
    @DisplayName("로그인 후 시간이 지난 세션을 회전해 저장하면 세션 키 TTL은 처음 세션 만료까지 남은 시간을 넘지 않는다")
    void rotatedSessionKeepsRemainingTtl() {
        store.save(ADMIN_ID, SESSION_A, "rotated-token", sessionExpiresIn(Duration.ofHours(1)));

        assertThat(redisTemplate.getExpire(REFRESH_KEY_A, TimeUnit.SECONDS)).isPositive()
                .isLessThanOrEqualTo(Duration.ofHours(1).toSeconds());
    }

    private Instant sessionExpiresIn(Duration remaining) {
        return Instant.now().plus(remaining);
    }

    @Test
    @DisplayName("사용자 세션 키(refresh:, sessions:)를 만들지 않는다")
    void doesNotUseUserKeys() {
        store.save(ADMIN_ID, SESSION_A, TOKEN_A, sessionExpiresIn(Duration.ofHours(12)));

        assertThat(redisTemplate.hasKey(USER_REFRESH_KEY_A)).isFalse();
        assertThat(redisTemplate.hasKey(USER_SESSIONS_KEY)).isFalse();
        assertThat(REFRESH_KEY_A).doesNotStartWith("refresh:");
        assertThat(SESSIONS_KEY).doesNotStartWith("sessions:");
    }

    @Test
    @DisplayName("같은 세션에서 다시 저장하면 이전 토큰은 더 이상 일치하지 않는다(회전)")
    void rotation() {
        store.save(ADMIN_ID, SESSION_A, TOKEN_A, sessionExpiresIn(Duration.ofHours(12)));
        store.save(ADMIN_ID, SESSION_A, "rotated-token", sessionExpiresIn(Duration.ofHours(12)));

        assertThat(store.matches(ADMIN_ID, SESSION_A, TOKEN_A)).isFalse();
        assertThat(store.matches(ADMIN_ID, SESSION_A, "rotated-token")).isTrue();
    }

    @Test
    @DisplayName("로그아웃은 그 세션만, 전체 삭제는 모든 세션을 지운다")
    void deleteAndDeleteAll() {
        store.save(ADMIN_ID, SESSION_A, TOKEN_A, sessionExpiresIn(Duration.ofHours(12)));
        store.save(ADMIN_ID, SESSION_B, TOKEN_B, sessionExpiresIn(Duration.ofHours(12)));

        store.delete(ADMIN_ID, SESSION_A);
        assertThat(store.matches(ADMIN_ID, SESSION_A, TOKEN_A)).isFalse();
        assertThat(store.matches(ADMIN_ID, SESSION_B, TOKEN_B)).isTrue();

        store.deleteAll(ADMIN_ID);
        assertThat(store.matches(ADMIN_ID, SESSION_B, TOKEN_B)).isFalse();
        assertThat(redisTemplate.hasKey(SESSIONS_KEY)).isFalse();
    }
}
