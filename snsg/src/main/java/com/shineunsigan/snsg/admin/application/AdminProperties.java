package com.shineunsigan.snsg.admin.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 어드민 토큰 수명과 로그인 잠금 정책. 토큰 만료·Redis TTL·쿠키 max-age가 모두 여기서 값을 가져와 서로 어긋나지 않는다.
 * <p>
 * presentation(쿠키)과 infrastructure(JWT·Redis)가 함께 읽는 값이라 application에 둔다.
 * 서명 키는 JWT 구현의 일이라 여기 두지 않는다. {@code ADMIN_JWT_SECRET} 환경변수가 relaxed binding으로
 * {@code admin.jwt.secret}에도 잡히므로 이 record에 {@code jwt} 필드를 두지 않는다.
 *
 * @param refreshTokenValidity 사용자(14일)보다 짧다. 데이터를 고칠 수 있는 권한이라 공용 PC에 로그인이 오래 남지 않게 한다
 * @param loginMaxFailures     같은 아이디로 연속 실패하면 잠그는 횟수
 * @param loginLockDuration    잠금 시간. 실패 횟수를 세는 기간이기도 하다
 */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(Duration accessTokenValidity, Duration refreshTokenValidity, int loginMaxFailures,
        Duration loginLockDuration) {
}
