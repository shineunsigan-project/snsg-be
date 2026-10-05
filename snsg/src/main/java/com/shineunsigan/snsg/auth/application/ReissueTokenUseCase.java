package com.shineunsigan.snsg.auth.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * refresh token으로 토큰을 다시 발급한다. 발급하면 이전 refresh token은 무효가 된다.
 * <p>
 * 사용자가 유효한지 매번 확인한다. 탈퇴 이벤트가 유실돼 토큰이 남아 있어도 여기서 막힌다.
 */
@Service
@RequiredArgsConstructor
public class ReissueTokenUseCase {

    private final TokenPort tokenPort;
    private final RefreshTokenStorePort refreshTokenStorePort;
    private final UserPort userPort;
    private final TokenIssuer tokenIssuer;

    public TokenResult reissue(String refreshToken) {
        UUID userId = parseUserId(refreshToken);

        if (!refreshTokenStorePort.matches(userId, refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (!userPort.exists(userId)) {
            refreshTokenStorePort.delete(userId);
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        return tokenIssuer.issue(userId);
    }

    public void logout(UUID userId) {
        refreshTokenStorePort.delete(userId);
    }

    private UUID parseUserId(String refreshToken) {
        try {
            return tokenPort.parseUserId(refreshToken);
        } catch (BusinessException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }
}
