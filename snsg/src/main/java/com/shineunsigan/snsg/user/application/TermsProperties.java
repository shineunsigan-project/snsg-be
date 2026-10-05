package com.shineunsigan.snsg.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 현재 시행 중인 약관 버전. 가입 시 동의 이력에 기록한다.
 * 약관 본문은 외부 문서(노션)에 있으므로, 개정하면 문서를 고치고 이 값만 올린다.
 */
@ConfigurationProperties(prefix = "terms")
public record TermsProperties(String privacyPolicyVersion, String serviceTermsVersion) {
}
