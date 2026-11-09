package com.shineunsigan.snsg.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.shineunsigan.snsg.user.domain.constants.AgePhase;

class AgeGroupTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    @ParameterizedTest(name = "만 {0}세 → {1} {2}")
    @CsvSource({
            "18, 10, LATE",
            "19, 10, LATE",
            "20, 20, EARLY",
            "23, 20, EARLY",
            "24, 20, MID",
            "26, 20, MID",
            "27, 20, LATE",
            "30, 30, EARLY"})
    @DisplayName("만 나이의 십의 자리가 decade, 일의 자리 0~3·4~6·7~9가 초반·중반·후반이다")
    void ageGroupByAge(int age, int decade, AgePhase phase) {
        AgeGroup ageGroup = AgeGroup.of(TODAY.minusYears(age), TODAY);

        assertThat(ageGroup.decade()).isEqualTo(decade);
        assertThat(ageGroup.phase()).isEqualTo(phase);
    }

    @Test
    @DisplayName("27세 생일 전날은 만 26세라 20대 중반이다")
    void dayBeforeTwentySeventhBirthday() {
        AgeGroup ageGroup = AgeGroup.of(LocalDate.of(1999, 11, 10), TODAY);

        assertThat(ageGroup).isEqualTo(new AgeGroup(20, AgePhase.MID));
    }

    @Test
    @DisplayName("27세 생일 당일은 만 27세라 20대 후반이다")
    void twentySeventhBirthday() {
        AgeGroup ageGroup = AgeGroup.of(LocalDate.of(1999, 11, 9), TODAY);

        assertThat(ageGroup).isEqualTo(new AgeGroup(20, AgePhase.LATE));
    }
}
