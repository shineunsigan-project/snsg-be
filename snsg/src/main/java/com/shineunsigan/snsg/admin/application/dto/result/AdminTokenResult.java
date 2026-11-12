package com.shineunsigan.snsg.admin.application.dto.result;

import java.time.Instant;

/**
 * 발급한 토큰 쌍. refresh는 쿠키로, access는 응답 본문으로 나가지만 그 결정은 presentation이 한다.
 *
 * @param refreshExpiresAt refresh token(세션)의 만료 시각. 쿠키 수명을 여기에 맞춘다
 */
public record AdminTokenResult(String accessToken, String refreshToken, Instant refreshExpiresAt) {
}
