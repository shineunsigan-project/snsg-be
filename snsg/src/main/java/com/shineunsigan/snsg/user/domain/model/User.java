package com.shineunsigan.snsg.user.domain.model;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

import com.shineunsigan.snsg.common.entity.BaseAuditEntity;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 탈퇴 여부는 {@code isDeleted}로 안다. 별도 상태 컬럼을 두지 않는다.
 * 운영자 정지처럼 삭제가 아닌 상태가 생기면 그때 상태 값을 더한다.
 * <p>
 * 이름·이메일은 남에게 보이지 않는 정보라 {@link Profile}이 아니라 여기에 둔다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseAuditEntity {

    /** 2~20자의 한글·영문. 공백·숫자는 받지 않는다. */
    public static final String NAME_PATTERN = "^[가-힣a-zA-Z]{2,20}$";

    /** {@code local@domain.tld} 모양. 최상위 도메인이 있어야 한다. 빈 문자열은 "없음"이라 통과한다. */
    public static final String EMAIL_PATTERN =
            "^([A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,})?$";

    private static final int EMAIL_MAX_LENGTH = 100;
    private static final int MIN_AGE = 18;

    private static final String NO_EMAIL = "";
    private static final String WITHDRAWN_NAME = "";
    private static final String WITHDRAWN_EMAIL = "";
    private static final Pattern NAME = Pattern.compile(NAME_PATTERN);
    private static final Pattern EMAIL = Pattern.compile(EMAIL_PATTERN);

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Embedded
    private Profile profile;

    private User(String name, String email, Profile profile) {
        this.name = name;
        this.email = email;
        this.profile = profile;
    }

    /**
     * 가입한다. 만 18세 판정은 {@code today}(가입일) 기준이다.
     * 이메일은 선택 입력이라 없으면({@code null} 또는 {@code ''}) {@code ''}로 둔다.
     */
    public static User create(String name, String email, Profile profile, LocalDate today) {
        validateName(name);
        String resolvedEmail = resolveEmail(email);
        validateEmail(resolvedEmail);
        validateAge(profile.getBirthDate(), today);
        return new User(name, resolvedEmail, profile);
    }

    public void updateProfile(Profile profile) {
        this.profile = profile;
    }

    /**
     * 탈퇴와 비식별 처리를 한 번에 한다. 둘을 나누면 호출하는 쪽에서 하나를 빠뜨려
     * 탈퇴했는데 개인정보가 남은 사용자가 생긴다.
     */
    public void withdraw() {
        if (isDeleted()) {
            throw new BusinessException(UserErrorCode.ALREADY_WITHDRAWN);
        }
        this.name = WITHDRAWN_NAME;
        this.email = WITHDRAWN_EMAIL;
        this.profile = profile.anonymized();
        delete();
    }

    private static String resolveEmail(String email) {
        return email == null ? NO_EMAIL : email;
    }

    private static void validateName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new BusinessException(UserErrorCode.INVALID_NAME);
        }
    }

    /**
     * 앞뒤 공백을 다듬지 않는다. 공백만 있는 값도 형식 오류다. 대소문자는 입력한 그대로 둔다.
     */
    private static void validateEmail(String email) {
        if (email.length() > EMAIL_MAX_LENGTH || !EMAIL.matcher(email).matches()) {
            throw new BusinessException(UserErrorCode.INVALID_EMAIL);
        }
    }

    /**
     * 만 나이는 {@link Period#between}으로 센다. 2월 29일생은 평년에는 3월 1일에 한 살을 먹는다.
     */
    private static void validateAge(LocalDate birthDate, LocalDate today) {
        if (Period.between(birthDate, today).getYears() < MIN_AGE) {
            throw new BusinessException(UserErrorCode.UNDERAGE);
        }
    }
}
