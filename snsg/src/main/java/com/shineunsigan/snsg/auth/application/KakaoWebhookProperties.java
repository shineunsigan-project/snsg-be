package com.shineunsigan.snsg.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오가 웹훅을 보낼 때 Authorization 헤더에 담아주는 어드민 키. 이 값으로 발신자를 확인한다.
 */
@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoWebhookProperties(String adminKey) {
}
