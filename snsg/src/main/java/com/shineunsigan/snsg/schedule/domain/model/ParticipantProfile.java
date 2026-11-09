package com.shineunsigan.snsg.schedule.domain.model;

import com.shineunsigan.snsg.schedule.domain.constants.Gender;

/**
 * 참여자 요약을 계산할 때 쓰는 확정 참여자 한 명의 값.
 *
 * @param gender   탈퇴자도 저장된 성별이다. 사용자 정보를 찾을 수 없으면 {@code null}
 * @param ageGroup 탈퇴자·사용자 정보를 찾을 수 없으면 {@code null}
 */
public record ParticipantProfile(Gender gender, ParticipantAgeGroup ageGroup) {
}
