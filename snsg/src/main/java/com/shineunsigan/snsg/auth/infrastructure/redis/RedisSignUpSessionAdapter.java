package com.shineunsigan.snsg.auth.infrastructure.redis;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.application.port.out.SignUpSessionPort;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 가입 세션을 Redis에 보관한다. 토큰만 있으면 그 소셜 계정으로 가입이 완료되므로
 * 추측할 수 없는 난수를 쓰고, 짧은 TTL을 걸고, 가입이 끝나면 지운다.
 */
@Component
@RequiredArgsConstructor
public class RedisSignUpSessionAdapter implements SignUpSessionPort {

    private static final String KEY_PREFIX = "signup:";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int TOKEN_BYTES = 32;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String save(SocialUserResult socialUser) {
        String signUpToken = generateToken();
        redisTemplate.opsForValue().set(key(signUpToken), socialUser, TTL);
        return signUpToken;
    }

    @Override
    public Optional<SocialUserResult> find(String signUpToken) {
        Object stored = redisTemplate.opsForValue().get(key(signUpToken));
        if (stored == null) {
            return Optional.empty();
        }
        // 저장 JSON에 타입 정보를 넣지 않는다. 클래스를 옮겨도 기존 세션이 깨지지 않고, 값이 사람이 읽을 수 있게 남는다.
        return Optional.of(objectMapper.convertValue(stored, SocialUserResult.class));
    }

    @Override
    public void delete(String signUpToken) {
        redisTemplate.delete(key(signUpToken));
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String key(String signUpToken) {
        return KEY_PREFIX + signUpToken;
    }
}
