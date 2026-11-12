package com.shineunsigan.snsg.admin.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.ErrorCode;

class AdminTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final String LOGIN_ID = "mintadmin";
    private static final String PASSWORD_HASH = "hashed-password";
    private static final String NAME = "김민트";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1996, 4, 12);

    @Nested
    @DisplayName("가입 신청")
    class SignUp {

        @Test
        @DisplayName("역할은 ADMIN, 상태는 승인 대기로 만든다")
        void pendingAdmin() {
            Admin admin = signUp(LOGIN_ID, NAME, AdminTeam.BACKEND, BIRTH_DATE);

            assertThat(admin.getRole()).isEqualTo(AdminRole.ADMIN);
            assertThat(admin.getStatus()).isEqualTo(AdminStatus.PENDING);
            assertThat(admin.isApproved()).isFalse();
            assertThat(admin.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Mintadmin", "mintAdmin", "1mint", "abc", "a12345678901234567890", "mint_admin",
                "mint admin", "민트관리자", ""})
        @DisplayName("아이디 형식이 틀리면 INVALID_LOGIN_ID: 대문자, 숫자로 시작, 3자, 21자, 기호·공백·한글")
        void invalidLoginId(String loginId) {
            assertError(() -> signUp(loginId, NAME, AdminTeam.BACKEND, BIRTH_DATE), AdminErrorCode.INVALID_LOGIN_ID);
        }

        @Test
        @DisplayName("아이디가 없으면 INVALID_LOGIN_ID")
        void nullLoginId() {
            assertError(() -> signUp(null, NAME, AdminTeam.BACKEND, BIRTH_DATE), AdminErrorCode.INVALID_LOGIN_ID);
        }

        @ParameterizedTest
        @ValueSource(strings = {"mint", "m1234567890123456789"})
        @DisplayName("아이디 경계: 4자와 20자는 통과한다")
        void loginIdBoundary(String loginId) {
            assertThat(signUp(loginId, NAME, AdminTeam.BACKEND, BIRTH_DATE).getLoginId()).isEqualTo(loginId);
        }

        @ParameterizedTest
        @ValueSource(strings = {"김", "김민트1", "김 민트", "가나다라마바사아자차카타파하가나다라마바사"})
        @DisplayName("이름 형식이 틀리면 INVALID_ADMIN_NAME: 1자, 숫자 포함, 공백 포함, 21자")
        void invalidName(String name) {
            assertError(() -> signUp(LOGIN_ID, name, AdminTeam.BACKEND, BIRTH_DATE), AdminErrorCode.INVALID_ADMIN_NAME);
        }

        @ParameterizedTest
        @ValueSource(strings = {"김민", "가나다라마바사아자차카타파하가나다라마바"})
        @DisplayName("이름 경계: 2자와 20자는 통과한다")
        void nameBoundary(String name) {
            assertThat(signUp(LOGIN_ID, name, AdminTeam.BACKEND, BIRTH_DATE).getName()).isEqualTo(name);
        }

        @Test
        @DisplayName("소속팀이 없으면 INVALID_ADMIN_TEAM")
        void nullTeam() {
            assertError(() -> signUp(LOGIN_ID, NAME, null, BIRTH_DATE), AdminErrorCode.INVALID_ADMIN_TEAM);
        }

        @Test
        @DisplayName("생년월일이 오늘·미래·없음이면 INVALID_ADMIN_BIRTH_DATE")
        void invalidBirthDate() {
            assertError(() -> signUp(LOGIN_ID, NAME, AdminTeam.BACKEND, TODAY),
                    AdminErrorCode.INVALID_ADMIN_BIRTH_DATE);
            assertError(() -> signUp(LOGIN_ID, NAME, AdminTeam.BACKEND, TODAY.plusDays(1)),
                    AdminErrorCode.INVALID_ADMIN_BIRTH_DATE);
            assertError(() -> signUp(LOGIN_ID, NAME, AdminTeam.BACKEND, null),
                    AdminErrorCode.INVALID_ADMIN_BIRTH_DATE);
        }

        @Test
        @DisplayName("생년월일 경계: 어제는 통과한다")
        void birthDateYesterday() {
            assertThat(signUp(LOGIN_ID, NAME, AdminTeam.BACKEND, TODAY.minusDays(1)).getBirthDate())
                    .isEqualTo(TODAY.minusDays(1));
        }
    }

    @Nested
    @DisplayName("가입 비밀번호 형식")
    class Password {

        @ParameterizedTest
        @ValueSource(strings = {"mint123", "mintmintmint", "12345678", "mint 1234", "민트mint1234", ""})
        @DisplayName("틀리면 INVALID_ADMIN_PASSWORD: 7자, 숫자 없음, 영문 없음, 공백 포함, 한글 포함, 빈 값")
        void invalid(String password) {
            assertError(() -> Admin.validatePassword(password), AdminErrorCode.INVALID_ADMIN_PASSWORD);
        }

        @Test
        @DisplayName("65자는 INVALID_ADMIN_PASSWORD, 없으면 INVALID_ADMIN_PASSWORD")
        void tooLongOrNull() {
            assertError(() -> Admin.validatePassword("a1" + "x".repeat(63)), AdminErrorCode.INVALID_ADMIN_PASSWORD);
            assertError(() -> Admin.validatePassword(null), AdminErrorCode.INVALID_ADMIN_PASSWORD);
        }

        @Test
        @DisplayName("경계: 8자와 64자, 특수문자는 통과한다")
        void boundary() {
            assertThatCode(() -> Admin.validatePassword("mint1234")).doesNotThrowAnyException();
            assertThatCode(() -> Admin.validatePassword("a1" + "x".repeat(62))).doesNotThrowAnyException();
            assertThatCode(() -> Admin.validatePassword("mint!@#$1~")).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("로그인 입력은 UTF-8 72바이트까지만 해시와 대조할 수 있다")
    void comparablePassword() {
        assertThat(Admin.isComparablePassword("a".repeat(72))).isTrue();
        assertThat(Admin.isComparablePassword("a".repeat(73))).isFalse();
        assertThat(Admin.isComparablePassword("가".repeat(64))).isFalse();
    }

    private Admin signUp(String loginId, String name, AdminTeam team, LocalDate birthDate) {
        return Admin.signUp(loginId, PASSWORD_HASH, name, team, birthDate, TODAY);
    }

    private void assertError(ThrowingCallable callable, ErrorCode errorCode) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
