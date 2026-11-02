package com.shineunsigan.snsg.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;

class UserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDate BIRTH_DATE = LocalDate.of(1996, 4, 12);

    @Test
    @DisplayName("가입하면 이름과 이메일이 입력한 그대로 저장된다")
    void createKeepsNameAndEmail() {
        User user = User.create("김민트", "User@Example.com", profile(BIRTH_DATE), TODAY);

        assertThat(user.getName()).isEqualTo("김민트");
        assertThat(user.getEmail()).isEqualTo("User@Example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("이메일을 생략하거나 빈 문자열로 보내면 빈 문자열로 저장된다")
    void emptyEmailStoredAsEmptyString(String email) {
        User user = User.create("김민트", email, profile(BIRTH_DATE), TODAY);

        assertThat(user.getEmail()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"김", "김 민트", "Mint Choco", "민트1", " 김민트", "김민트 ", "김민트!",
            "가나다라마바사아자차카타파하가나다라마바사"})
    @DisplayName("이름이 2~20자의 한글·영문이 아니면 INVALID_NAME")
    void invalidName(String name) {
        assertErrorCode(() -> User.create(name, "", profile(BIRTH_DATE), TODAY), UserErrorCode.INVALID_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"김민트", "Mint", "MintChoco", "김Mint", "가나다라마바사아자차카타파하가나다라마바"})
    @DisplayName("2~20자의 한글·영문 이름은 허용한다")
    void validName(String name) {
        User user = User.create(name, "", profile(BIRTH_DATE), TODAY);

        assertThat(user.getName()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@b", " a@b.com", "a@b.com ", "   ", "mint", "mint@", "@example.com", "mint@example.",
            "mint@@example.com", "민트@example.com"})
    @DisplayName("이메일이 local@domain.tld 형식이 아니면 INVALID_EMAIL")
    void invalidEmail(String email) {
        assertErrorCode(() -> User.create("김민트", email, profile(BIRTH_DATE), TODAY), UserErrorCode.INVALID_EMAIL);
    }

    @Test
    @DisplayName("이메일이 100자를 넘으면 INVALID_EMAIL. 100자는 허용한다")
    void emailMaxLength() {
        String hundred = "a".repeat(88) + "@example.com";
        String hundredOne = "a".repeat(89) + "@example.com";

        assertThat(User.create("김민트", hundred, profile(BIRTH_DATE), TODAY).getEmail()).hasSize(100);
        assertErrorCode(() -> User.create("김민트", hundredOne, profile(BIRTH_DATE), TODAY),
                UserErrorCode.INVALID_EMAIL);
    }

    @Test
    @DisplayName("만 18세 생일 당일은 가입할 수 있다")
    void eighteenthBirthday() {
        User user = User.create("김민트", "", profile(LocalDate.of(2008, 10, 28)), TODAY);

        assertThat(user.getProfile().getBirthDate()).isEqualTo(LocalDate.of(2008, 10, 28));
    }

    @Test
    @DisplayName("만 18세 생일 전날이면 UNDERAGE")
    void dayBeforeEighteenthBirthday() {
        assertErrorCode(() -> User.create("김민트", "", profile(LocalDate.of(2008, 10, 29)), TODAY),
                UserErrorCode.UNDERAGE);
    }

    @Test
    @DisplayName("2월 29일생은 평년 2월 28일에는 아직 만 18세가 아니다")
    void leapDayBirthBeforeMarchFirst() {
        Profile profile = profile(LocalDate.of(2008, 2, 29));

        assertErrorCode(() -> User.create("김민트", "", profile, LocalDate.of(2026, 2, 28)), UserErrorCode.UNDERAGE);
    }

    @Test
    @DisplayName("2월 29일생은 평년 3월 1일에 만 18세가 된다")
    void leapDayBirthOnMarchFirst() {
        User user = User.create("김민트", "", profile(LocalDate.of(2008, 2, 29)), LocalDate.of(2026, 3, 1));

        assertThat(user.getProfile().getBirthDate()).isEqualTo(LocalDate.of(2008, 2, 29));
    }

    @Test
    @DisplayName("탈퇴하면 삭제 표시와 비식별 처리가 함께 일어난다")
    void withdraw() {
        User user = createUser();

        user.withdraw();

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getProfile().getNickname()).isNotEqualTo("민트초코");
        assertThat(user.getProfile().getProfileImageUrl()).isEqualTo(Profile.DEFAULT_PROFILE_IMAGE_URL);
    }

    @Test
    @DisplayName("탈퇴하면 이름과 이메일이 즉시 빈 문자열이 된다")
    void withdrawClearsNameAndEmail() {
        User user = createUser();

        user.withdraw();

        assertThat(user.getName()).isEmpty();
        assertThat(user.getEmail()).isEmpty();
    }

    @Test
    @DisplayName("이미 탈퇴한 사용자는 다시 탈퇴할 수 없다")
    void withdrawTwice() {
        User user = createUser();
        user.withdraw();

        assertErrorCode(user::withdraw, UserErrorCode.ALREADY_WITHDRAWN);
    }

    @Test
    @DisplayName("프로필을 수정하면 새 값으로 바뀐다")
    void updateProfile() {
        User user = createUser();

        user.updateProfile(Profile.of("민트라떼", Gender.MALE, LocalDate.of(1990, 1, 1), null));

        assertThat(user.getProfile().getNickname()).isEqualTo("민트라떼");
        assertThat(user.getProfile().getGender()).isEqualTo(Gender.MALE);
    }

    private User createUser() {
        return User.create("김민트", "mint@example.com",
                Profile.of("민트초코", Gender.FEMALE, BIRTH_DATE, "https://kakao.test/me.png"), TODAY);
    }

    private Profile profile(LocalDate birthDate) {
        return Profile.of("민트초코", Gender.FEMALE, birthDate, null);
    }

    private void assertErrorCode(ThrowingCallable callable, ErrorCode errorCode) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
