package com.shineunsigan.snsg.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.NicknameHistoryReason;

class UserNicknameHistoryTest {

    private static final Duration PERIOD = Duration.ofDays(14);
    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Test
    @DisplayName("변경 이력은 이전 닉네임을 변경 시각 + 보호 기간까지 보호한다")
    void changed() {
        User user = User.create("김민트", "",
                Profile.of("민트라떼", Gender.FEMALE, LocalDate.of(1996, 4, 12), null), LocalDate.of(2026, 11, 1));

        UserNicknameHistory history = UserNicknameHistory.changed(user, "민트초코", CHANGED_AT, PERIOD);

        assertThat(history.getUser()).isSameAs(user);
        assertThat(history.getNickname()).isEqualTo("민트초코");
        assertThat(history.getReason()).isEqualTo(NicknameHistoryReason.CHANGED);
        assertThat(history.getReservedUntil()).isEqualTo(CHANGED_AT.plusDays(14));
    }

    @Test
    @DisplayName("바꾼 적이 없으면 지금 바꿀 수 있다")
    void noHistory() {
        Optional<LocalDateTime> changeableAt =
                UserNicknameHistory.nicknameChangeableAt(Optional.empty(), PERIOD, CHANGED_AT);

        assertThat(changeableAt).isEmpty();
    }

    @Test
    @DisplayName("마지막 변경 후 13일 23시간 59분이면 바꿀 수 없고, 변경 시각 + 14일에 바꿀 수 있다")
    void withinRestriction() {
        LocalDateTime now = CHANGED_AT.plusDays(13).plusHours(23).plusMinutes(59);

        Optional<LocalDateTime> changeableAt =
                UserNicknameHistory.nicknameChangeableAt(Optional.of(CHANGED_AT), PERIOD, now);

        assertThat(changeableAt).contains(CHANGED_AT.plusDays(14));
    }

    @Test
    @DisplayName("마지막 변경 후 정확히 14일이면 바꿀 수 있다")
    void exactlyAfterRestriction() {
        Optional<LocalDateTime> changeableAt = UserNicknameHistory.nicknameChangeableAt(
                Optional.of(CHANGED_AT), PERIOD, CHANGED_AT.plusDays(14));

        assertThat(changeableAt).isEmpty();
    }

    @Test
    @DisplayName("마지막 변경 후 14일이 지났으면 바꿀 수 있다")
    void afterRestriction() {
        Optional<LocalDateTime> changeableAt = UserNicknameHistory.nicknameChangeableAt(
                Optional.of(CHANGED_AT), PERIOD, CHANGED_AT.plusDays(20));

        assertThat(changeableAt).isEmpty();
    }
}
