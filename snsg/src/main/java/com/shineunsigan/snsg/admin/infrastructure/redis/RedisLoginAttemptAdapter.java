package com.shineunsigan.snsg.admin.infrastructure.redis;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.admin.application.port.out.LoginAttemptPort;

/**
 * 어드민 로그인 연속 실패를 Redis에 센다.
 * <p>
 * {@code admin-login-fail:{loginId}}에 실패 횟수(INCR)를, 잠기면 {@code admin-login-lock:{loginId}}를 둔다.
 * 두 키 모두 TTL이 잠금 시간이라 정리 작업이 없다. 실패 사이가 잠금 시간보다 벌어지면 횟수가 사라진다.
 * 키 두 개를 함께 바꾸며 TTL을 거는 곳은 MULTI/EXEC로 묶어 TTL 없는 키가 남지 않게 한다.
 */
@Component
public class RedisLoginAttemptAdapter implements LoginAttemptPort {

    private static final String FAILURE_KEY_PREFIX = "admin-login-fail:";
    private static final String LOCK_KEY_PREFIX = "admin-login-lock:";
    private static final String LOCKED = "1";

    private final StringRedisTemplate redisTemplate;

    public RedisLoginAttemptAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isLocked(String loginId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(loginId)));
    }

    @Override
    public boolean recordFailure(String loginId, int maxFailures, Duration lockDuration) {
        long failures = increaseFailures(loginId, lockDuration);
        if (failures < maxFailures) {
            return false;
        }
        lock(loginId, lockDuration);
        return true;
    }

    @Override
    public void reset(String loginId) {
        redisTemplate.delete(List.of(failureKey(loginId), lockKey(loginId)));
    }

    /** INCR와 EXPIRE를 묶는다. 따로 보내면 INCR 직후 실패할 때 횟수 키가 TTL 없이 남는다. */
    private long increaseFailures(String loginId, Duration lockDuration) {
        String failureKey = failureKey(loginId);
        List<Object> results = executeInTransaction(operations -> {
            operations.opsForValue().increment(failureKey);
            operations.expire(failureKey, lockDuration);
        });
        return (Long) results.get(0);
    }

    /** 잠금 키를 만들고 횟수를 지운다. 잠금이 풀리면 처음부터 다시 센다. */
    private void lock(String loginId, Duration lockDuration) {
        executeInTransaction(operations -> {
            operations.opsForValue().set(lockKey(loginId), LOCKED, lockDuration);
            operations.delete(failureKey(loginId));
        });
    }

    private List<Object> executeInTransaction(Consumer<RedisOperations<String, String>> commands) {
        return redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> stringOperations = (RedisOperations<String, String>) operations;
                stringOperations.multi();
                commands.accept(stringOperations);
                return stringOperations.exec();
            }
        });
    }

    private String failureKey(String loginId) {
        return FAILURE_KEY_PREFIX + loginId;
    }

    private String lockKey(String loginId) {
        return LOCK_KEY_PREFIX + loginId;
    }
}
