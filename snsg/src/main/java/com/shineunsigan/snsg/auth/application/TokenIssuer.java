package com.shineunsigan.snsg.auth.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;

import lombok.RequiredArgsConstructor;

/**
 * 토큰 발급과 보관은 로그인·가입·재발급에서 똑같이 일어난다. 한 곳에 둔다.
 * 재발급하면 이전 refresh token은 무효가 된다. 사용자당 하나만 유효하다.
 */
@Component
@RequiredArgsConstructor
class TokenIssuer {

    private final TokenPort tokenPort;
    private final RefreshTokenStorePort refreshTokenStorePort;

    TokenResult issue(UUID userId) {
        String accessToken = tokenPort.issueAccessToken(userId);
        String refreshToken = tokenPort.issueRefreshToken(userId);
        refreshTokenStorePort.save(userId, refreshToken);
        return new TokenResult(accessToken, refreshToken);
    }
}
