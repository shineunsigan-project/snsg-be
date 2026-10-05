package com.shineunsigan.snsg.auth.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.application.port.out.SignUpSessionPort;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;

@SpringBootTest
class RedisSignUpSessionAdapterTest {

    private static final SocialUserResult SOCIAL_USER =
            new SocialUserResult(SocialProvider.KAKAO, "kakao-1", "민트초코", "https://kakao.test/me.png");

    @Autowired
    private SignUpSessionPort signUpSessionPort;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("저장한 소셜 정보를 토큰으로 다시 꺼낸다")
    void saveAndFind() {
        String signUpToken = signUpSessionPort.save(SOCIAL_USER);

        assertThat(signUpSessionPort.find(signUpToken)).contains(SOCIAL_USER);

        signUpSessionPort.delete(signUpToken);
    }

    @Test
    @DisplayName("가입을 마치면 토큰을 다시 쓸 수 없다")
    void deletedTokenCannotBeReused() {
        String signUpToken = signUpSessionPort.save(SOCIAL_USER);

        signUpSessionPort.delete(signUpToken);

        assertThat(signUpSessionPort.find(signUpToken)).isEmpty();
    }

    @Test
    @DisplayName("없는 토큰이면 비어 있다")
    void unknownToken() {
        assertThat(signUpSessionPort.find("모르는-토큰")).isEmpty();
    }

    @Test
    @DisplayName("토큰은 매번 다르고 추측할 수 없을 만큼 길며 만료 시간이 걸린다")
    void tokenIsRandomAndExpires() {
        String first = signUpSessionPort.save(SOCIAL_USER);
        String second = signUpSessionPort.save(SOCIAL_USER);

        assertThat(first).isNotEqualTo(second).hasSizeGreaterThanOrEqualTo(40);
        assertThat(redisTemplate.getExpire("signup:" + first)).isPositive();

        signUpSessionPort.delete(first);
        signUpSessionPort.delete(second);
    }
}
