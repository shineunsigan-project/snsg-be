package com.shineunsigan.snsg.user.domain.model;

import java.time.LocalDate;
import java.util.regex.Pattern;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 남에게 보여줄 내 정보. 프로필 수정에서 함께 바뀌고 탈퇴 시 함께 비식별 처리된다.
 * 값을 바꾸지 않고 새 인스턴스를 만든다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

    public static final String DEFAULT_PROFILE_IMAGE_URL = "/images/default-profile.png";

    /** 2~10자의 한글·영문·숫자. 기능명세서 POL-005. */
    public static final String NICKNAME_PATTERN = "^[가-힣a-zA-Z0-9]{2,10}$";

    private static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";
    private static final LocalDate WITHDRAWN_BIRTH_DATE = LocalDate.of(1900, 1, 1);
    private static final Pattern NICKNAME = Pattern.compile(NICKNAME_PATTERN);

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String profileImageUrl;

    private Profile(String nickname, Gender gender, LocalDate birthDate, String profileImageUrl) {
        this.nickname = nickname;
        this.gender = gender;
        this.birthDate = birthDate;
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 소셜에서 받은 사진이 없으면 기본 이미지로 채운다. 컬럼을 NOT NULL로 유지하기 위해서다.
     */
    public static Profile of(String nickname, Gender gender, LocalDate birthDate, String profileImageUrl) {
        validateNickname(nickname);
        validateBirthDate(birthDate);
        return new Profile(nickname, gender, birthDate, resolveProfileImageUrl(profileImageUrl));
    }

    /**
     * 탈퇴 시 개인정보를 지운다. 성별은 남긴다. 단독으로는 개인을 식별할 수 없고,
     * 과거 일정의 참여자 구성(남 2 · 여 2)을 보여주는 데 쓰인다.
     */
    public Profile anonymized() {
        return new Profile(WITHDRAWN_NICKNAME, gender, WITHDRAWN_BIRTH_DATE, DEFAULT_PROFILE_IMAGE_URL);
    }

    private static String resolveProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return DEFAULT_PROFILE_IMAGE_URL;
        }
        return profileImageUrl;
    }

    private static void validateNickname(String nickname) {
        if (nickname == null || !NICKNAME.matcher(nickname).matches()) {
            throw new BusinessException(UserErrorCode.INVALID_NICKNAME);
        }
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
            throw new BusinessException(UserErrorCode.INVALID_BIRTH_DATE);
        }
    }
}
