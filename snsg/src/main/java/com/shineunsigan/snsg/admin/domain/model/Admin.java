package com.shineunsigan.snsg.admin.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.regex.Pattern;

import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.entity.BaseAuditEntity;
import com.shineunsigan.snsg.common.exception.BusinessException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운영 도구에 로그인하는 어드민 계정. 서비스 사용자({@code users})와 테이블·토큰·인증을 모두 따로 둔다.
 * <p>
 * 가입 신청은 항상 {@link AdminRole#ADMIN}·{@link AdminStatus#PENDING}이다. MASTER는 API로 만들지 않고 DB에 직접 넣는다.
 * 승인한 사람을 남기기 위해 {@link BaseAuditEntity}를 상속한다.
 */
@Getter
@Entity
@Table(name = "admins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseAuditEntity {

    /** 영문 소문자로 시작, 영문 소문자·숫자 4~20자. 소문자만 받으므로 대소문자 무시 비교가 필요 없다. */
    public static final String LOGIN_ID_PATTERN = "^[a-z][a-z0-9]{3,19}$";

    /** 8~64자, 영문·숫자 각 1자 이상, 공백 없는 ASCII 출력 문자. 최대 64바이트라 BCrypt 한계(72바이트) 안이다. */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,64}$";

    /** 2~20자의 한글·영문. 사용자 가입의 실명 규칙과 같다. */
    public static final String NAME_PATTERN = "^[가-힣a-zA-Z]{2,20}$";

    /** 비밀번호 해시(BCrypt)가 보는 최대 바이트. */
    private static final int HASHABLE_PASSWORD_MAX_BYTES = 72;

    private static final Pattern LOGIN_ID = Pattern.compile(LOGIN_ID_PATTERN);
    private static final Pattern PASSWORD = Pattern.compile(PASSWORD_PATTERN);
    private static final Pattern NAME = Pattern.compile(NAME_PATTERN);

    @Column(nullable = false)
    private String loginId;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminTeam team;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminStatus status;

    private Admin(String loginId, String passwordHash, String name, AdminTeam team, LocalDate birthDate) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.team = team;
        this.birthDate = birthDate;
        this.role = AdminRole.ADMIN;
        this.status = AdminStatus.PENDING;
    }

    /**
     * 가입 신청한다. 비밀번호는 원문이 아니라 해시를 받는다. 원문 형식은 해시를 만들기 전에
     * {@link #validatePassword}로 본다. 생년월일은 {@code today}(한국 시간)보다 이전이어야 한다.
     */
    public static Admin signUp(String loginId, String passwordHash, String name, AdminTeam team,
            LocalDate birthDate, LocalDate today) {
        validateLoginId(loginId);
        validateName(name);
        validateTeam(team);
        validateBirthDate(birthDate, today);
        return new Admin(loginId, passwordHash, name, team, birthDate);
    }

    /**
     * 가입 비밀번호의 형식을 본다. 해시를 만들기 전에 부른다. 틀린 비밀번호로 해시를 만들지 않는다.
     */
    public static void validatePassword(String rawPassword) {
        if (rawPassword == null || !PASSWORD.matcher(rawPassword).matches()) {
            throw new BusinessException(AdminErrorCode.INVALID_ADMIN_PASSWORD);
        }
    }

    /**
     * 로그인 입력을 해시와 대조할 수 있는 길이인가. BCrypt는 72바이트를 넘는 입력을 받지 않는다.
     * 넘으면(한글 64자 등) 대조하지 않고 틀린 비밀번호로 본다. 가입 규칙상 저장된 비밀번호는 64바이트 이하다.
     */
    public static boolean isComparablePassword(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length <= HASHABLE_PASSWORD_MAX_BYTES;
    }

    public boolean isApproved() {
        return status == AdminStatus.APPROVED;
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID.matcher(loginId).matches()) {
            throw new BusinessException(AdminErrorCode.INVALID_LOGIN_ID);
        }
    }

    private static void validateName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new BusinessException(AdminErrorCode.INVALID_ADMIN_NAME);
        }
    }

    private static void validateTeam(AdminTeam team) {
        if (team == null) {
            throw new BusinessException(AdminErrorCode.INVALID_ADMIN_TEAM);
        }
    }

    private static void validateBirthDate(LocalDate birthDate, LocalDate today) {
        if (birthDate == null || !birthDate.isBefore(today)) {
            throw new BusinessException(AdminErrorCode.INVALID_ADMIN_BIRTH_DATE);
        }
    }
}
