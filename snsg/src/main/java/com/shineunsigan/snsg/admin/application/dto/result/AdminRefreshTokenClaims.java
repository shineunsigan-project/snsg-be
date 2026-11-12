package com.shineunsigan.snsg.admin.application.dto.result;

import java.time.Instant;
import java.util.UUID;

/**
 * 어드민 refresh token에 담긴 값. sessionId는 로그인한 기기(세션)를 가리킨다.
 *
 * @param expiresAt 세션 만료 시각. 로그인 때 정해지고 재발급해도 바뀌지 않는다
 */
public record AdminRefreshTokenClaims(UUID adminId, String sessionId, Instant expiresAt) {
}
