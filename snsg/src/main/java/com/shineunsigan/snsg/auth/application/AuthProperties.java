package com.shineunsigan.snsg.auth.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토큰과 가입 세션의 수명. 토큰 만료·Redis TTL·쿠키 max-age가 모두 여기서 값을 가져와 서로 어긋나지 않는다.
 * <p>
 * presentation(쿠키)과 infrastructure(JWT·Redis)가 함께 읽는 값이라 application에 둔다.
 * 서명 키는 JWT 구현의 일이므로 여기 두지 않는다.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Duration accessTokenValidity, Duration refreshTokenValidity, Duration signupSessionTtl) {
}
