package com.shineunsigan.snsg.auth.application.dto.result;

/**
 * 발급한 토큰 쌍. refresh는 쿠키로, access는 응답 본문으로 나가지만
 * 그 결정은 presentation이 한다. application은 둘 다 돌려줄 뿐이다.
 */
public record TokenResult(String accessToken, String refreshToken) {
}
