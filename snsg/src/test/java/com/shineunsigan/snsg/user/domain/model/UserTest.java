package com.shineunsigan.snsg.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;

class UserTest {

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
    @DisplayName("이미 탈퇴한 사용자는 다시 탈퇴할 수 없다")
    void withdrawTwice() {
        User user = createUser();
        user.withdraw();

        assertThatThrownBy(user::withdraw)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.ALREADY_WITHDRAWN);
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
        return User.create(
                Profile.of("민트초코", Gender.FEMALE, LocalDate.of(1996, 4, 12), "https://kakao.test/me.png"));
    }
}
