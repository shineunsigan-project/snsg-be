package com.shineunsigan.snsg.auth.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;

/**
 * 실제 Redis에 붙어 확인한다. 저장 형태(해시)와 TTL은 우리가 정한 규칙이다.
 */
@SpringBootTest
class RedisRefreshTokenStoreAdapterTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String REFRESH_TOKEN = "refresh-token-value";

    @Autowired
    private RefreshTokenStorePort refreshTokenStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void clear() {
        redisTemplate.delete("refresh:" + USER_ID);
    }

    @Test
    @DisplayName("저장한 토큰과 같으면 일치로 본다")
    void matches() {
        refreshTokenStore.save(USER_ID, REFRESH_TOKEN);

        assertThat(refreshTokenStore.matches(USER_ID, REFRESH_TOKEN)).isTrue();
        assertThat(refreshTokenStore.matches(USER_ID, "다른-토큰")).isFalse();
    }

    @Test
    @DisplayName("저장된 적이 없으면 일치하지 않는다")
    void notStored() {
        assertThat(refreshTokenStore.matches(USER_ID, REFRESH_TOKEN)).isFalse();
    }

    @Test
    @DisplayName("재발급하면 이전 토큰은 무효가 된다")
    void replacedOnReissue() {
        refreshTokenStore.save(USER_ID, REFRESH_TOKEN);
        refreshTokenStore.save(USER_ID, "새-토큰");

        assertThat(refreshTokenStore.matches(USER_ID, REFRESH_TOKEN)).isFalse();
        assertThat(refreshTokenStore.matches(USER_ID, "새-토큰")).isTrue();
    }

    @Test
    @DisplayName("삭제하면 더는 일치하지 않는다")
    void delete() {
        refreshTokenStore.save(USER_ID, REFRESH_TOKEN);

        refreshTokenStore.delete(USER_ID);

        assertThat(refreshTokenStore.matches(USER_ID, REFRESH_TOKEN)).isFalse();
    }

    @Test
    @DisplayName("토큰 원문이 아니라 해시를 저장하고 만료 시간을 건다")
    void storesHashWithTtl() {
        refreshTokenStore.save(USER_ID, REFRESH_TOKEN);

        String stored = redisTemplate.opsForValue().get("refresh:" + USER_ID);
        assertThat(stored).isNotNull().isNotEqualTo(REFRESH_TOKEN).hasSize(64);
        assertThat(redisTemplate.getExpire("refresh:" + USER_ID)).isPositive();
    }
}
