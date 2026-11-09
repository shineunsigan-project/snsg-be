package com.shineunsigan.snsg.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 탈퇴 회원 비식별 배치 설정. 보관 기간은 닉네임 보호 기간과 같은 값이라 {@link NicknameProperties}에 있다.
 * <p>
 * prefix를 {@code user}로 두지 않는다. OS 환경변수 {@code USER}와 부딪힌다.
 * 실행 시각({@code withdrawal.anonymize-cron})은 {@code @Scheduled}가 자리표시자로 읽어 여기 두지 않는다.
 *
 * @param anonymizePageSize 비식별 대상을 한 번에 읽는 건수
 */
@ConfigurationProperties(prefix = "withdrawal")
public record WithdrawalProperties(int anonymizePageSize) {
}
