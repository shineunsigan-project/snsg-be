package com.shineunsigan.snsg.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;

class ProfileTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1996, 4, 12);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "열한글자가넘는닉네임임"})
    @DisplayName("닉네임이 비었거나 10자를 넘으면 INVALID_NICKNAME")
    void invalidNickname(String nickname) {
        assertThatThrownBy(() -> Profile.of(nickname, Gender.FEMALE, BIRTH_DATE, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_NICKNAME);
    }

    @Test
    @DisplayName("생년월일이 미래면 INVALID_BIRTH_DATE")
    void futureBirthDate() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> Profile.of("민트초코", Gender.FEMALE, tomorrow, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_BIRTH_DATE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("소셜에서 받은 프로필 사진이 없으면 기본 이미지로 채운다")
    void fillsDefaultProfileImage(String profileImageUrl) {
        Profile profile = Profile.of("민트초코", Gender.FEMALE, BIRTH_DATE, profileImageUrl);

        assertThat(profile.getProfileImageUrl()).isEqualTo(Profile.DEFAULT_PROFILE_IMAGE_URL);
    }

    @Test
    @DisplayName("비식별 처리는 닉네임·사진·생년월일을 바꾸고 성별은 남긴다")
    void anonymized() {
        Profile profile = Profile.of("민트초코", Gender.FEMALE, BIRTH_DATE, "https://kakao.test/me.png");

        Profile anonymized = profile.anonymized();

        assertThat(anonymized.getNickname()).isNotEqualTo("민트초코");
        assertThat(anonymized.getProfileImageUrl()).isEqualTo(Profile.DEFAULT_PROFILE_IMAGE_URL);
        assertThat(anonymized.getBirthDate()).isNotEqualTo(BIRTH_DATE);
        assertThat(anonymized.getGender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("비식별 처리는 원본을 바꾸지 않는다")
    void anonymizedDoesNotMutateOriginal() {
        Profile profile = Profile.of("민트초코", Gender.FEMALE, BIRTH_DATE, "https://kakao.test/me.png");

        profile.anonymized();

        assertThat(profile.getNickname()).isEqualTo("민트초코");
        assertThat(profile.getBirthDate()).isEqualTo(BIRTH_DATE);
    }
}
