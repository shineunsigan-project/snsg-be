package com.shineunsigan.snsg.auth.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.shineunsigan.snsg.auth.application.AuthProperties;
import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;

/**
 * 실제 Redis에 붙어 확인한다. 세션 단위 키 구조, 저장 형태(해시)와 TTL은 우리가 정한 규칙이다.
 * 공유 Redis라 고정 사용자·세션을 쓰고, 만든 키는 이름으로 지운다.
 */
@SpringBootTest
class RedisRefreshTokenStoreAdapterTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String SESSION_A = "session-a";
    private static final String SESSION_B = "session-b";
    private static final String TOKEN_A = "refresh-token-a";
    private static final String TOKEN_B = "refresh-token-b";

    private static final String REFRESH_KEY_A = "refresh:" + USER_ID + ":" + SESSION_A;
    private static final String REFRESH_KEY_B = "refresh:" + USER_ID + ":" + SESSION_B;
    private static final String SESSIONS_KEY = "sessions:" + USER_ID;
    private static final String LEGACY_KEY = "refresh:" + USER_ID;

    @Autowired
    private RefreshTokenStorePort refreshTokenStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AuthProperties authProperties;

    @AfterEach
    void clear() {
        redisTemplate.delete(List.of(REFRESH_KEY_A, REFRESH_KEY_B, SESSIONS_KEY, LEGACY_KEY));
    }

    @Test
    @DisplayName("같은 사용자의 세션은 각자의 토큰만 일치로 본다")
    void sessionsAreIndependent() {
        refreshTokenStore.save(USER_ID, SESSION_A, TOKEN_A);
        refreshTokenStore.save(USER_ID, SESSION_B, TOKEN_B);

        assertThat(refreshTokenStore.matches(USER_ID, SESSION_A, TOKEN_A)).isTrue();
        assertThat(refreshTokenStore.matches(USER_ID, SESSION_B, TOKEN_B)).isTrue();
        assertThat(refreshTokenStore.matches(USER_ID, SESSION_B, TOKEN_A)).isFalse();
        assertThat(refreshTokenStore.matches(USER_ID, SESSION_A, TOKEN_B)).isFalse();
    }

    @Test
    @DisplayName("저장된 적이 없는 세션은 일치하지 않는다")
    void notStored() {
        assertThat(refreshTokenStore.matches(USER_ID, SESSION_A, TOKEN_A)).isFalse();
    }

    @Test
    @DisplayName("같은 세션에서 재발급하면 이전 토큰은 무효가 된다")
    void replacedOnReissue() {
        refreshTokenStore.save(USER_ID, SESSION_A, TOKEN_A);
        refreshTokenStore.save(USER_ID, SESSION_A, "새-토큰");

        assertThat(refreshTokenStore.matches(USER_ID, SESSION_A, TOKEN_A)).isFalse();
        assertThat(refreshTokenStore.matches(USER_ID, SESSION_A, "새-토큰")).isTrue();
    }

    @Test
    @DisplayName("로그아웃은 그 세션만 지우고 다른 세션은 남긴다")
    void deleteOneSession() {
        refreshTokenStore.save(USER_ID, SESSION_A, TOKEN_A);
        refreshTokenStore.save(USER_ID, SESSION_B, TOKEN_B);

        refreshTokenStore.delete(USER_ID, SESSION_A);

        assertThat(refreshTokenStore.matches(USER_ID, SESSION_A, TOKEN_A)).isFalse();
        assertThat(refreshTokenStore.matches(USER_ID, SESSION_B, TOKEN_B)).isTrue();
        assertThat(redisTemplate.opsForSet().members(SESSIONS_KEY)).containsExactly(SESSION_B);
    }

    @Test
    @DisplayName("전체 삭제는 모든 세션 키와 세션 집합을 지운다")
    void deleteAllSessions() {
        refreshTokenStore.save(USER_ID, SESSION_A, TOKEN_A);
        refreshTokenStore.save(USER_ID, SESSION_B, TOKEN_B);

        refreshTokenStore.deleteAll(USER_ID);

        assertThat(refreshTokenStore.matches(USER_ID, SESSION_A, TOKEN_A)).isFalse();
        assertThat(refreshTokenStore.matches(USER_ID, SESSION_B, TOKEN_B)).isFalse();
        assertThat(redisTemplate.hasKey(REFRESH_KEY_A)).isFalse();
        assertThat(redisTemplate.hasKey(REFRESH_KEY_B)).isFalse();
        assertThat(redisTemplate.hasKey(SESSIONS_KEY)).isFalse();
    }

    @Test
    @DisplayName("토큰 원문이 아니라 해시를 저장하고 두 키 모두 만료 시간을 건다")
    void storesHashWithTtl() {
        refreshTokenStore.save(USER_ID, SESSION_A, TOKEN_A);

        String stored = redisTemplate.opsForValue().get(REFRESH_KEY_A);
        assertThat(stored).isNotNull().isNotEqualTo(TOKEN_A).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(redisTemplate.getExpire(REFRESH_KEY_A, TimeUnit.SECONDS)).isPositive();
        assertThat(redisTemplate.getExpire(SESSIONS_KEY, TimeUnit.SECONDS)).isPositive();
    }

    @Test
    @DisplayName("세션을 저장할 때마다 세션 집합의 만료 시간을 다시 건다")
    void refreshesSessionsTtlOnSave() {
        refreshTokenStore.save(USER_ID, SESSION_A, TOKEN_A);
        redisTemplate.expire(SESSIONS_KEY, 60, TimeUnit.SECONDS);

        refreshTokenStore.save(USER_ID, SESSION_B, TOKEN_B);

        long validitySeconds = authProperties.refreshTokenValidity().toSeconds();
        assertThat(redisTemplate.getExpire(SESSIONS_KEY, TimeUnit.SECONDS))
                .isGreaterThan(validitySeconds - 60)
                .isLessThanOrEqualTo(validitySeconds);
    }

    @Test
    @DisplayName("세션 없는 이전 형식의 키는 만들지 않는다")
    void doesNotCreateLegacyKey() {
        refreshTokenStore.save(USER_ID, SESSION_A, TOKEN_A);

        assertThat(redisTemplate.hasKey(LEGACY_KEY)).isFalse();
    }
}
