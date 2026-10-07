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

    private static final SocialUserResult OTHER_SOCIAL_USER =
            new SocialUserResult(SocialProvider.KAKAO, "kakao-2", "딸기우유", "https://kakao.test/other.png");

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
        String second = signUpSessionPort.save(OTHER_SOCIAL_USER);

        assertThat(first).isNotEqualTo(second).hasSizeGreaterThanOrEqualTo(40);
        assertThat(redisTemplate.getExpire("signup:" + first)).isPositive();

        signUpSessionPort.delete(first);
        signUpSessionPort.delete(second);
    }

    @Test
    @DisplayName("같은 소셜 계정으로 다시 로그인하면 이전 토큰은 쓸 수 없다")
    void onlyLatestTokenSurvivesPerSocialAccount() {
        String previous = signUpSessionPort.save(SOCIAL_USER);
        String latest = signUpSessionPort.save(SOCIAL_USER);

        assertThat(signUpSessionPort.find(previous)).isEmpty();
        assertThat(signUpSessionPort.find(latest)).contains(SOCIAL_USER);

        signUpSessionPort.delete(latest);
    }

    @Test
    @DisplayName("다른 소셜 계정의 토큰은 서로 지우지 않는다")
    void tokensOfDifferentAccountsCoexist() {
        String mine = signUpSessionPort.save(SOCIAL_USER);
        String others = signUpSessionPort.save(OTHER_SOCIAL_USER);

        assertThat(signUpSessionPort.find(mine)).contains(SOCIAL_USER);
        assertThat(signUpSessionPort.find(others)).contains(OTHER_SOCIAL_USER);

        signUpSessionPort.delete(mine);
        signUpSessionPort.delete(others);
    }

    @Test
    @DisplayName("가입을 마치면 색인도 함께 지워져 다음 로그인에 영향을 주지 않는다")
    void deleteRemovesOwnerIndex() {
        String signUpToken = signUpSessionPort.save(SOCIAL_USER);
        signUpSessionPort.delete(signUpToken);

        assertThat(redisTemplate.hasKey("signup:owner:KAKAO:kakao-1")).isFalse();
    }
}
