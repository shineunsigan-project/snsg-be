package com.shineunsigan.snsg.auth.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.auth.application.dto.result.RefreshTokenClaims;
import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * refresh token으로 토큰을 다시 발급하고, 로그아웃으로 기기 세션을 끝낸다.
 * <p>
 * 재발급은 refresh token이 가리키는 세션의 토큰만 바꾼다. 그 세션의 이전 refresh token은 무효가 되고,
 * 다른 기기의 세션은 그대로다.
 * <p>
 * 사용자가 유효한지 매번 확인한다. 탈퇴 이벤트가 유실돼 세션이 남아 있어도 여기서 막힌다.
 */
@Service
@RequiredArgsConstructor
public class ReissueTokenFacade {

    private final TokenPort tokenPort;
    private final RefreshTokenStorePort refreshTokenStorePort;
    private final UserPort userPort;
    private final TokenIssuer tokenIssuer;

    public TokenResult reissue(String refreshToken) {
        RefreshTokenClaims claims = parseRefreshToken(refreshToken);
        UUID userId = claims.userId();

        if (!refreshTokenStorePort.matches(userId, claims.sessionId(), refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (!userPort.exists(userId)) {
            // 탈퇴 이벤트와 같은 결과를 낸다. 이 경로는 이벤트가 유실됐을 때의 안전망이다.
            refreshTokenStorePort.deleteAll(userId);
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        return tokenIssuer.reissue(userId, claims.sessionId());
    }

    /**
     * refresh token이 가리키는 이 기기의 세션만 지운다. 다른 기기의 로그인은 유지된다.
     * <p>
     * 쿠키가 없거나 해석할 수 없거나 다른 사용자의 것이면 아무것도 지우지 않고 끝낸다.
     * 로그아웃은 재시도해도 안전해야 하고, 남의 세션을 지워서는 안 된다.
     */
    public void logout(UUID userId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        RefreshTokenClaims claims;
        try {
            claims = tokenPort.parseRefreshToken(refreshToken);
        } catch (BusinessException exception) {
            return;
        }
        if (!claims.userId().equals(userId)) {
            return;
        }
        refreshTokenStorePort.delete(userId, claims.sessionId());
    }

    private RefreshTokenClaims parseRefreshToken(String refreshToken) {
        try {
            return tokenPort.parseRefreshToken(refreshToken);
        } catch (BusinessException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }
}
