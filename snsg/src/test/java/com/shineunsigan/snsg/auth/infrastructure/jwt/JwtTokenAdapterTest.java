package com.shineunsigan.snsg.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shineunsigan.snsg.auth.application.AuthProperties;
import com.shineunsigan.snsg.auth.application.dto.result.RefreshTokenClaims;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtTokenAdapterTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "another-secret-key-that-is-also-32-bytes-long";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String SESSION_ID = "session-a";

    private final JwtTokenAdapter tokenAdapter = adapterWith(SECRET, Duration.ofMinutes(30));

    @Test
    @DisplayName("발급한 토큰에서 사용자 식별자를 복원한다")
    void issueAndParse() {
        String accessToken = tokenAdapter.issueAccessToken(USER_ID);

        assertThat(tokenAdapter.parseUserId(accessToken)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("refresh token도 같은 식별자를 담고, 세션 식별자를 함께 담는다")
    void refreshTokenCarriesUserIdAndSessionId() {
        String refreshToken = tokenAdapter.issueRefreshToken(USER_ID, SESSION_ID);

        RefreshTokenClaims claims = tokenAdapter.parseRefreshToken(refreshToken);

        assertThat(claims.userId()).isEqualTo(USER_ID);
        assertThat(claims.sessionId()).isEqualTo(SESSION_ID);
    }

    @Test
    @DisplayName("access token은 refresh token으로 받지 않는다")
    void rejectsAccessTokenAsRefreshToken() {
        String accessToken = tokenAdapter.issueAccessToken(USER_ID);

        assertThatThrownBy(() -> tokenAdapter.parseRefreshToken(accessToken))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("세션 식별자가 없는 이전 형식의 refresh token은 거부한다")
    void rejectsRefreshTokenWithoutSessionId() {
        Date now = new Date();
        String legacyRefreshToken = Jwts.builder()
                .subject(USER_ID.toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + Duration.ofDays(14).toMillis()))
                .signWith(signingKey())
                .compact();

        assertThatThrownBy(() -> tokenAdapter.parseRefreshToken(legacyRefreshToken))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("access token에는 세션 식별자를 담지 않는다")
    void accessTokenHasNoSessionId() {
        String accessToken = tokenAdapter.issueAccessToken(USER_ID);

        Claims claims = Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(accessToken).getPayload();

        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims).doesNotContainKey(JwtTokenAdapter.SESSION_ID_CLAIM);
    }

    @Test
    @DisplayName("refresh token은 access token 자리에 쓸 수 없다")
    void rejectsRefreshTokenAsAccessToken() {
        String refreshToken = tokenAdapter.issueRefreshToken(USER_ID, SESSION_ID);

        assertThatThrownBy(() -> tokenAdapter.parseUserId(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN);
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

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private JwtTokenAdapter adapterWith(String secret, Duration validity) {
        return new JwtTokenAdapter(new JwtProperties(secret), new AuthProperties(validity, validity, validity));
    }
}
