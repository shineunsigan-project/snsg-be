package com.shineunsigan.snsg.user.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 닉네임 정책 기간.
 * <p>
 * prefix를 {@code user}로 두지 않는다. 운영 서버(리눅스)의 OS 환경변수 {@code USER}가 {@code user} 속성으로 잡혀
 * 이 record 바인딩이 실패할 수 있다.
 *
 * @param protectAfterChange 바꾸기 전 닉네임을 보호하는 기간이자, 닉네임을 다시 바꿀 수 없는 기간.
 *                           두 규칙이 같은 값을 쓰므로 한 설정에서 읽는다
 */
@ConfigurationProperties(prefix = "nickname")
public record NicknameProperties(Duration protectAfterChange) {
}
