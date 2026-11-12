package com.shineunsigan.snsg.admin.application;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.admin.application.dto.result.AdminTokenResult;
import com.shineunsigan.snsg.admin.application.port.out.AdminRefreshTokenStorePort;
import com.shineunsigan.snsg.admin.application.port.out.AdminTokenPort;

/**
 * 어드민 토큰 발급과 보관. 로그인은 새 기기 세션을 만들고, 재발급은 같은 세션의 토큰만 바꾼다(회전).
 * <p>
 * 세션은 <b>로그인 시각부터 refresh 수명(12시간)에 끝난다(절대 만료).</b> 재발급은 처음 정한 세션 만료를 그대로 이어받아
 * 새 refresh token의 만료·Redis TTL·쿠키 수명이 모두 남은 시간이 된다. 계속 써도 세션이 늘어나지 않아
 * 공용 PC에 로그인이 하루를 넘겨 남지 않는다. 사용자 앱의 refresh(재발급마다 14일)와 다르다.
 * <p>
 * access token도 세션 만료를 넘지 않는다(30분과 남은 시간 중 짧은 쪽). 세션이 끝난 뒤에도 access로
 * 최대 30분 더 쓸 수 있으면 절대 만료가 그만큼 밀린다.
 */
@Component
class AdminTokenIssuer {

    private final AdminTokenPort adminTokenPort;
    private final AdminRefreshTokenStorePort adminRefreshTokenStorePort;
    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;

    AdminTokenIssuer(AdminTokenPort adminTokenPort, AdminRefreshTokenStorePort adminRefreshTokenStorePort,
            AdminProperties adminProperties) {
        this.adminTokenPort = adminTokenPort;
        this.adminRefreshTokenStorePort = adminRefreshTokenStorePort;
        this.accessTokenValidity = adminProperties.accessTokenValidity();
        this.refreshTokenValidity = adminProperties.refreshTokenValidity();
    }

    /**
     * 새 세션으로 발급한다. sessionId는 추측할 수 없도록 난수(UUID v4)로 만든다.
     * 세션 만료는 초 단위로 자른다. JWT 만료가 초 단위라 재발급 때 토큰에서 읽은 값과 같아진다.
     */
    AdminTokenResult issue(UUID adminId) {
        Instant now = Instant.now();
        Instant sessionExpiresAt = now.plus(refreshTokenValidity).truncatedTo(ChronoUnit.SECONDS);
        return issueUntil(adminId, UUID.randomUUID().toString(), sessionExpiresAt, now);
    }

    /**
     * 같은 세션으로 다시 발급한다. 세션 만료는 처음 로그인 때 정한 값을 그대로 쓴다.
     */
    AdminTokenResult reissue(UUID adminId, String sessionId, Instant sessionExpiresAt) {
        return issueUntil(adminId, sessionId, sessionExpiresAt, Instant.now());
    }

    private AdminTokenResult issueUntil(UUID adminId, String sessionId, Instant sessionExpiresAt, Instant now) {
        Instant accessExpiresAt = earlier(now.plus(accessTokenValidity), sessionExpiresAt);
        String accessToken = adminTokenPort.issueAccessToken(adminId, accessExpiresAt);
        String refreshToken = adminTokenPort.issueRefreshToken(adminId, sessionId, sessionExpiresAt);
        adminRefreshTokenStorePort.save(adminId, sessionId, refreshToken, sessionExpiresAt);
        return new AdminTokenResult(accessToken, refreshToken, sessionExpiresAt);
    }

    private Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
