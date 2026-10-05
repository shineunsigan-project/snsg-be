package com.shineunsigan.snsg.auth.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(Provider kakao, Provider naver) {

    public record Provider(String clientId, String clientSecret, String redirectUri) {
    }
}
