package com.shineunsigan.snsg.admin.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        /** 동작 이름, 허용된 출발 상태, 도착 상태. */
        static Stream<Arguments> transitions() {
            return Stream.of(
                    Arguments.of("approve", AdminStatus.PENDING, AdminStatus.APPROVED),
                    Arguments.of("reject", AdminStatus.PENDING, AdminStatus.REJECTED),
                    Arguments.of("suspend", AdminStatus.APPROVED, AdminStatus.SUSPENDED),
                    Arguments.of("unsuspend", AdminStatus.SUSPENDED, AdminStatus.APPROVED));
        }

        @ParameterizedTest(name = "{0}: {1} → {2}")
        @MethodSource("transitions")
        @DisplayName("허용된 출발 상태에서 부르면 도착 상태가 된다")
        void allowed(String action, AdminStatus from, AdminStatus to) {
            Admin admin = adminIn(from);

            act(admin, action);

            assertThat(admin.getStatus()).isEqualTo(to);
        }

        /** 나머지 세 상태에는 같은 동작을 다시 부른 경우(승인 후 승인, 중지 후 중지 등)가 들어 있다. */
        @ParameterizedTest(name = "{0}: {1}이 아닌 세 상태")
        @MethodSource("transitions")
        @DisplayName("허용된 출발 상태가 아닌 나머지 세 상태에서 부르면 ADMIN_STATUS_CONFLICT이고 상태는 그대로다")
        void conflict(String action, AdminStatus from, AdminStatus to) {
            EnumSet<AdminStatus> others = EnumSet.complementOf(EnumSet.of(from));
            assertThat(others).hasSize(3);
            for (AdminStatus other : others) {
                Admin admin = adminIn(other);

                assertError(() -> act(admin, action), AdminErrorCode.ADMIN_STATUS_CONFLICT);
                assertThat(admin.getStatus()).isEqualTo(other);
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("transitions")
        @DisplayName("대상이 MASTER면 어느 상태에서든 MASTER_NOT_MODIFIABLE이고 상태는 그대로다")
        void master(String action, AdminStatus from, AdminStatus to) throws Exception {
            for (AdminStatus status : AdminStatus.values()) {
                Admin admin = adminIn(status);
                makeMaster(admin);

                assertError(() -> act(admin, action), AdminErrorCode.MASTER_NOT_MODIFIABLE);
                assertThat(admin.getStatus()).isEqualTo(status);
            }
        }

        @Test
        @DisplayName("승인 상태만 승인된 것으로 본다. 거절·이용 중지는 승인되지 않은 것이다(인증·재발급이 막힌다)")
        void approvedOnlyInApproved() {
            assertThat(adminIn(AdminStatus.APPROVED).isApproved()).isTrue();
            assertThat(adminIn(AdminStatus.REJECTED).isApproved()).isFalse();
            assertThat(adminIn(AdminStatus.SUSPENDED).isApproved()).isFalse();
        }

        /** 가입 신청부터 허용된 전이만 거쳐 그 상태의 ADMIN을 만든다. */
        private Admin adminIn(AdminStatus status) {
            Admin admin = signUp(LOGIN_ID, NAME, AdminTeam.BACKEND, BIRTH_DATE);
            switch (status) {
                case PENDING -> { }
                case APPROVED -> admin.approve();
                case REJECTED -> admin.reject();
                case SUSPENDED -> {
                    admin.approve();
                    admin.suspend();
                }
            }
            return admin;
        }

        /** MASTER는 API로 만들지 않고 DB에 넣으므로 만드는 메서드가 없다. 테스트에서만 역할을 바꾼다. */
        private void makeMaster(Admin admin) throws ReflectiveOperationException {
            Field role = Admin.class.getDeclaredField("role");
            role.setAccessible(true);
            role.set(admin, AdminRole.MASTER);
        }

        private void act(Admin admin, String action) {
            switch (action) {
                case "approve" -> admin.approve();
                case "reject" -> admin.reject();
                case "suspend" -> admin.suspend();
                case "unsuspend" -> admin.unsuspend();
                default -> throw new IllegalArgumentException(action);
            }
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
