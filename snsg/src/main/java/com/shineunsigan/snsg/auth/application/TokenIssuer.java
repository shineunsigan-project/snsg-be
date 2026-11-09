package com.shineunsigan.snsg.auth.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;

import lombok.RequiredArgsConstructor;

/**
 * 토큰 발급과 보관은 로그인·가입·재발급에서 똑같이 일어난다. 한 곳에 둔다.
 * <p>
 * 로그인·가입은 새 기기 세션을 만들고, 재발급은 같은 세션의 토큰만 바꾼다.
 * 재발급하면 그 세션의 이전 refresh token은 무효가 된다. 다른 기기의 세션은 영향을 받지 않는다.
 */
@Component
@RequiredArgsConstructor
class TokenIssuer {

    private final TokenPort tokenPort;
    private final RefreshTokenStorePort refreshTokenStorePort;

    /**
     * 새 세션으로 발급한다. sessionId는 추측할 수 없도록 난수(UUID v4)로 만든다.
     */
    TokenResult issue(UUID userId) {
        return reissue(userId, UUID.randomUUID().toString());
    }

    TokenResult reissue(UUID userId, String sessionId) {
        String accessToken = tokenPort.issueAccessToken(userId);
        String refreshToken = tokenPort.issueRefreshToken(userId, sessionId);
        refreshTokenStorePort.save(userId, sessionId, refreshToken);
        return new TokenResult(accessToken, refreshToken);
    }
}
