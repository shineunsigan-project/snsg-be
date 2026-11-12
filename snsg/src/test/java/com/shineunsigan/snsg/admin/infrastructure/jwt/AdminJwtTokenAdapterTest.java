package com.shineunsigan.snsg.admin.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shineunsigan.snsg.admin.application.dto.result.AdminRefreshTokenClaims;
import com.shineunsigan.snsg.auth.application.AuthProperties;
import com.shineunsigan.snsg.auth.infrastructure.jwt.JwtProperties;
import com.shineunsigan.snsg.auth.infrastructure.jwt.JwtTokenAdapter;
import com.shineunsigan.snsg.common.exception.BusinessException;

/**
 * 사용자 토큰과 어드민 토큰이 서로의 자리에서 통하지 않는지 실제 두 adapter(서로 다른 키)로 본다.
 * 테스트 코드라 auth를 import해도 된다(ArchUnit은 프로덕션 코드만 본다).
 */
class AdminJwtTokenAdapterTest {

    private static final String USER_SECRET = "user-secret-key-must-be-at-least-32-bytes-long";
    private static final String ADMIN_SECRET = "admin-secret-key-must-be-at-least-32-bytes-long";
    private static final Duration VALIDITY = Duration.ofMinutes(30);
    private static final UUID ID = UUID.randomUUID();
    private static final String SESSION_ID = "session-a";

    private final AdminJwtTokenAdapter adminAdapter = new AdminJwtTokenAdapter(new AdminJwtProperties(ADMIN_SECRET));
    /** 세션 만료. JWT 만료가 초 단위라 초로 자른다. */
    private final Instant expiresAt = Instant.now().plus(VALIDITY).truncatedTo(ChronoUnit.SECONDS);
    private final JwtTokenAdapter userAdapter = new JwtTokenAdapter(new JwtProperties(USER_SECRET),
            new AuthProperties(VALIDITY, VALIDITY, VALIDITY));

    @Test
    @DisplayName("어드민 access는 어드민 adapter에서 adminId로, 어드민 refresh는 adminId와 세션으로 해석된다")
    void adminTokens() {
        assertThat(adminAdapter.parseAdminId(adminAdapter.issueAccessToken(ID, expiresAt))).isEqualTo(ID);
        assertThat(adminAdapter.parseRefreshToken(adminAdapter.issueRefreshToken(ID, SESSION_ID, expiresAt)))
                .isEqualTo(new AdminRefreshTokenClaims(ID, SESSION_ID, expiresAt));
    }

    @Test
    @DisplayName("만료 시각이 지난 어드민 refresh·access는 해석에서 실패한다(세션 만료 뒤 재발급 불가)")
    void expiredTokensRejected() {
        Instant past = Instant.now().minusSeconds(5);

        assertRejected(() -> adminAdapter.parseRefreshToken(adminAdapter.issueRefreshToken(ID, SESSION_ID, past)));
        assertRejected(() -> adminAdapter.parseAdminId(adminAdapter.issueAccessToken(ID, past)));
    }

    @Test
    @DisplayName("사용자 access·refresh는 어드민 adapter의 access·refresh 해석에서 모두 실패한다")
    void userTokensRejectedByAdmin() {
        String userAccess = userAdapter.issueAccessToken(ID);
        String userRefresh = userAdapter.issueRefreshToken(ID, SESSION_ID);

        assertRejected(() -> adminAdapter.parseAdminId(userAccess));
        assertRejected(() -> adminAdapter.parseAdminId(userRefresh));
        assertRejected(() -> adminAdapter.parseRefreshToken(userAccess));
        assertRejected(() -> adminAdapter.parseRefreshToken(userRefresh));
    }

    @Test
    @DisplayName("어드민 access·refresh는 사용자 adapter의 access·refresh 해석에서 모두 실패한다")
    void adminTokensRejectedByUser() {
        String adminAccess = adminAdapter.issueAccessToken(ID, expiresAt);
        String adminRefresh = adminAdapter.issueRefreshToken(ID, SESSION_ID, expiresAt);

        assertRejected(() -> userAdapter.parseUserId(adminAccess));
        assertRejected(() -> userAdapter.parseUserId(adminRefresh));
        assertRejected(() -> userAdapter.parseRefreshToken(adminAccess));
        assertRejected(() -> userAdapter.parseRefreshToken(adminRefresh));
    }

    @Test
    @DisplayName("어드민 refresh는 access 자리에서, 어드민 access는 refresh 자리에서 실패한다")
    void adminTokenKindsAreNotInterchangeable() {
        String adminAccess = adminAdapter.issueAccessToken(ID, expiresAt);
        String adminRefresh = adminAdapter.issueRefreshToken(ID, SESSION_ID, expiresAt);

        assertRejected(() -> adminAdapter.parseAdminId(adminRefresh));
        assertRejected(() -> adminAdapter.parseRefreshToken(adminAccess));
    }

    private void assertRejected(ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(BusinessException.class);
    }
}
