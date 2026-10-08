package com.shineunsigan.snsg.auth.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(Kakao kakao, Provider naver) {

    public record Provider(String clientId, String clientSecret, String redirectUri) {
    }

    /**
     * 어드민 키는 사용자 토큰 없이 서버가 직접 호출할 때 쓴다(연동 해제, 웹훅 검증).
     * 앱 전체를 다룰 수 있는 키라 클라이언트에 내려보내지 않는다.
     */
    public record Kakao(String clientId, String clientSecret, String redirectUri, String adminKey) {

        public Provider toProvider() {
            return new Provider(clientId, clientSecret, redirectUri);
        }
    }
}
