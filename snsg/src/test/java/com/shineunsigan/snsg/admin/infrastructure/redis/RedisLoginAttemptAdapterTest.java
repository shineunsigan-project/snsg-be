package com.shineunsigan.snsg.admin.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.shineunsigan.snsg.admin.application.port.out.LoginAttemptPort;

/**
 * 실제 Redis에 붙어 실패 횟수·잠금 키와 TTL을 본다. 공유 Redis라 고정 아이디를 쓰고, 만든 키는 이름으로 지운다.
 */
@SpringBootTest
class RedisLoginAttemptAdapterTest {

    private static final String LOGIN_ID = "testlockfixed01";
    private static final String FAILURE_KEY = "admin-login-fail:" + LOGIN_ID;
    private static final String LOCK_KEY = "admin-login-lock:" + LOGIN_ID;
    private static final int MAX_FAILURES = 3;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(7);

    @Autowired
    private LoginAttemptPort loginAttemptPort;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void clear() {
        redisTemplate.delete(List.of(FAILURE_KEY, LOCK_KEY));
    }

    @Test
    @DisplayName("실패를 기록하면 횟수가 늘고 횟수 키에 TTL이 걸린다. 설정 횟수 전에는 잠기지 않는다")
    void countsFailuresWithTtl() {
        assertThat(loginAttemptPort.recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION)).isFalse();
        assertThat(loginAttemptPort.recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION)).isFalse();

        assertThat(redisTemplate.opsForValue().get(FAILURE_KEY)).isEqualTo("2");
        assertThat(redisTemplate.getExpire(FAILURE_KEY, TimeUnit.SECONDS))
                .isPositive()
                .isLessThanOrEqualTo(LOCK_DURATION.toSeconds());
        assertThat(loginAttemptPort.isLocked(LOGIN_ID)).isFalse();
    }

    @Test
    @DisplayName("설정 횟수에 이르면 잠김으로 보이고 잠금 키의 TTL은 잠금 시간 이하다")
    void locksAtMaxFailures() {
        loginAttemptPort.recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION);
        loginAttemptPort.recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION);

        assertThat(loginAttemptPort.recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION)).isTrue();

        assertThat(loginAttemptPort.isLocked(LOGIN_ID)).isTrue();
        assertThat(redisTemplate.getExpire(LOCK_KEY, TimeUnit.SECONDS))
                .isPositive()
                .isLessThanOrEqualTo(LOCK_DURATION.toSeconds());
    }

    @Test
    @DisplayName("초기화하면 잠김이 아니고 횟수가 0이다")
    void reset() {
        for (int i = 0; i < MAX_FAILURES; i++) {
            loginAttemptPort.recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION);
        }

        loginAttemptPort.reset(LOGIN_ID);

        assertThat(loginAttemptPort.isLocked(LOGIN_ID)).isFalse();
        assertThat(redisTemplate.hasKey(FAILURE_KEY)).isFalse();
        assertThat(loginAttemptPort.recordFailure(LOGIN_ID, MAX_FAILURES, LOCK_DURATION)).isFalse();
        assertThat(redisTemplate.opsForValue().get(FAILURE_KEY)).isEqualTo("1");
    }
}
