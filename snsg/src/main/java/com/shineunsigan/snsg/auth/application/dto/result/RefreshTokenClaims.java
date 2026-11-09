package com.shineunsigan.snsg.auth.application.dto.result;

import java.util.UUID;

/**
 * refresh token에 담긴 값. sessionId는 로그인한 기기(세션)를 가리킨다.
 */
public record RefreshTokenClaims(UUID userId, String sessionId) {
}
