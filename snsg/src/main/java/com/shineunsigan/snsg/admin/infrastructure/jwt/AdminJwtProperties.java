package com.shineunsigan.snsg.admin.infrastructure.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 어드민 토큰 서명 키. 사용자 키({@code jwt.secret})와 다른 값이어야 한다.
 * 같으면 한쪽 토큰이 다른 쪽 서명 검증을 통과해 분리가 깨진다.
 */
@ConfigurationProperties(prefix = "admin-jwt")
public record AdminJwtProperties(String secret) {
}
