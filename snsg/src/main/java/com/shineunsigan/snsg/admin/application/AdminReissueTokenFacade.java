package com.shineunsigan.snsg.admin.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.admin.application.dto.result.AdminRefreshTokenClaims;
import com.shineunsigan.snsg.admin.application.dto.result.AdminTokenResult;
import com.shineunsigan.snsg.admin.application.port.out.AdminRefreshTokenStorePort;
import com.shineunsigan.snsg.admin.application.port.out.AdminTokenPort;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 어드민 refresh token으로 토큰을 다시 발급하고, 로그아웃으로 기기 세션을 끝낸다.
 * <p>
 * 재발급은 refresh token이 가리키는 세션의 토큰만 바꾼다(회전). 세션 만료(로그인 후 12시간)는 늘리지 않고 이어받는다.
 * 어드민이 승인 상태인지 매번 다시 본다.
 * 승인 상태가 아니면(없음·삭제·승인 대기) 그 어드민의 세션을 모두 지운다.
 */
@Service
@RequiredArgsConstructor
public class AdminReissueTokenFacade {

    private final AdminTokenPort adminTokenPort;
    private final AdminRefreshTokenStorePort adminRefreshTokenStorePort;
    private final AdminQueryService adminQueryService;
    private final AdminTokenIssuer adminTokenIssuer;

    public AdminTokenResult reissue(String refreshToken) {
        AdminRefreshTokenClaims claims = parseRefreshToken(refreshToken);
        UUID adminId = claims.adminId();

        if (!claims.expiresAt().isAfter(Instant.now())) {
            // 토큰 해석이 만료를 이미 거르지만, 남은 시간이 없는 세션으로 발급하지 않도록 한 번 더 본다.
            throw new BusinessException(AdminErrorCode.INVALID_ADMIN_REFRESH_TOKEN);
        }

        if (!adminRefreshTokenStorePort.matches(adminId, claims.sessionId(), refreshToken)) {
            throw new BusinessException(AdminErrorCode.INVALID_ADMIN_REFRESH_TOKEN);
        }
        if (!adminQueryService.isApproved(adminId)) {
            adminRefreshTokenStorePort.deleteAll(adminId);
            throw new BusinessException(AdminErrorCode.INVALID_ADMIN_REFRESH_TOKEN);
        }

        return adminTokenIssuer.reissue(adminId, claims.sessionId(), claims.expiresAt());
    }

    /**
     * refresh token이 가리키는 이 기기의 세션만 지운다.
     * 쿠키가 없거나 해석할 수 없거나 다른 어드민의 것이면 아무것도 지우지 않는다. 재시도해도 안전하다.
     */
    public void logout(UUID adminId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        AdminRefreshTokenClaims claims;
        try {
            claims = adminTokenPort.parseRefreshToken(refreshToken);
        } catch (BusinessException exception) {
            return;
        }
        if (!claims.adminId().equals(adminId)) {
            return;
        }
        adminRefreshTokenStorePort.delete(adminId, claims.sessionId());
    }

    private AdminRefreshTokenClaims parseRefreshToken(String refreshToken) {
        try {
            return adminTokenPort.parseRefreshToken(refreshToken);
        } catch (BusinessException exception) {
            throw new BusinessException(AdminErrorCode.INVALID_ADMIN_REFRESH_TOKEN);
        }
    }
}
