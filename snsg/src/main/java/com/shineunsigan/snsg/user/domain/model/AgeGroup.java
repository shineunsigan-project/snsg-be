package com.shineunsigan.snsg.user.domain.model;

import java.time.LocalDate;
import java.time.Period;

import com.shineunsigan.snsg.user.domain.constants.AgePhase;

/**
 * 연령대. 생년월일 대신 남에게 보여준다(예: 20대 후반).
 * 만 나이의 십의 자리가 {@code decade}, 일의 자리가 {@code phase}를 정한다. 18·19세는 10대 후반이다.
 *
 * @param decade 십의 자리 × 10 (20대면 20)
 */
public record AgeGroup(int decade, AgePhase phase) {

    private static final int MID_FROM = 4;
    private static final int LATE_FROM = 7;

    /**
     * 만 나이는 {@link Period#between}으로 센다. 기준일은 조회 시점의 오늘이다.
     */
    public static AgeGroup of(LocalDate birthDate, LocalDate today) {
        int age = Period.between(birthDate, today).getYears();
        return new AgeGroup(age / 10 * 10, phaseOf(age % 10));
    }

    private static AgePhase phaseOf(int digit) {
        if (digit >= LATE_FROM) {
            return AgePhase.LATE;
        }
        if (digit >= MID_FROM) {
            return AgePhase.MID;
        }
        return AgePhase.EARLY;
    }
}
