package com.shineunsigan.snsg.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

class JwtTokenAdapterTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "another-secret-key-that-is-also-32-bytes-long";
    private static final UUID USER_ID = UUID.randomUUID();

    private final JwtTokenAdapter tokenAdapter = adapterWith(SECRET, Duration.ofMinutes(30));

    @Test
    @DisplayName("발급한 토큰에서 사용자 식별자를 복원한다")
    void issueAndParse() {
        String accessToken = tokenAdapter.issueAccessToken(USER_ID);

        assertThat(tokenAdapter.parseUserId(accessToken)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("refresh token도 같은 식별자를 담는다")
    void refreshTokenCarriesUserId() {
        String refreshToken = tokenAdapter.issueRefreshToken(USER_ID);

        assertThat(tokenAdapter.parseUserId(refreshToken)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 거부한다")
    void rejectsTokenSignedWithAnotherKey() {
        String forged = adapterWith(OTHER_SECRET, Duration.ofMinutes(30)).issueAccessToken(USER_ID);

        assertThatThrownBy(() -> tokenAdapter.parseUserId(forged))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    @DisplayName("만료된 토큰은 거부한다")
    void rejectsExpiredToken() {
        String expired = adapterWith(SECRET, Duration.ofSeconds(-10)).issueAccessToken(USER_ID);

        assertThatThrownBy(() -> tokenAdapter.parseUserId(expired))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    @DisplayName("형식이 깨진 문자열도 같은 오류로 거부한다")
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> tokenAdapter.parseUserId("not-a-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }

    private JwtTokenAdapter adapterWith(String secret, Duration validity) {
        return new JwtTokenAdapter(new JwtProperties(secret, validity, validity));
    }
}
