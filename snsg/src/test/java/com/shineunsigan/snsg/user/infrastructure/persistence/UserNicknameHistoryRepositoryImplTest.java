package com.shineunsigan.snsg.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.shineunsigan.snsg.common.config.JpaAuditingConfig;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.NicknameHistoryReason;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.model.UserNicknameHistory;

import jakarta.persistence.EntityManager;

/**
 * 이력 엔티티가 V8 스키마와 맞는지와, 마지막 변경 시각 조회가 어떤 행을 보는지 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserNicknameHistoryRepositoryImpl.class, UserRepositoryImpl.class, JpaAuditingConfig.class})
class UserNicknameHistoryRepositoryImplTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Autowired
    private UserNicknameHistoryRepositoryImpl historyRepository;

    @Autowired
    private UserRepositoryImpl userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("팩토리로 만든 이력을 저장하면 사용자·닉네임·사유·보호 기한이 그대로 읽힌다")
    void saveAndRead() {
        User user = userRepository.save(createUser("민트라떼"));
        UserNicknameHistory saved = historyRepository.save(
                UserNicknameHistory.changed(user, "민트초코", BASE, Duration.ofDays(14)));
        entityManager.flush();
        entityManager.clear();

        UserNicknameHistory found = entityManager.find(UserNicknameHistory.class, saved.getId());

        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getNickname()).isEqualTo("민트초코");
        assertThat(found.getReason()).isEqualTo(NicknameHistoryReason.CHANGED);
        assertThat(found.getReservedUntil()).isEqualTo(BASE.plusDays(14));
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이력이 여럿이면 가장 최근 변경 시각을 돌려준다")
    void lastChangedAt() {
        User user = userRepository.save(createUser("민트라떼"));
        insertHistory(user, "CHANGED", BASE, false);
        insertHistory(user, "CHANGED", BASE.plusDays(20), false);
        insertHistory(user, "CHANGED", BASE.plusDays(5), false);

        assertThat(historyRepository.findLastChangedAt(user.getId())).contains(BASE.plusDays(20));
    }

    @Test
    @DisplayName("다른 사용자의 이력, 삭제된 이력, 변경이 아닌 이력은 보지 않는다")
    void ignoresOtherUsersDeletedAndOtherReasons() {
        User user = userRepository.save(createUser("민트라떼"));
        User other = userRepository.save(createUser("레몬티"));
        insertHistory(user, "CHANGED", BASE, false);
        insertHistory(other, "CHANGED", BASE.plusDays(3), false);
        insertHistory(user, "CHANGED", BASE.plusDays(2), true);
        insertHistory(user, "WITHDRAWN", BASE.plusDays(1), false);

        assertThat(historyRepository.findLastChangedAt(user.getId())).contains(BASE);
    }

    @Test
    @DisplayName("이력이 없으면 빈 값")
    void noHistory() {
        User user = userRepository.save(createUser("민트라떼"));

        assertThat(historyRepository.findLastChangedAt(user.getId())).isEmpty();
    }

    /** 변경 시각(created_at)은 Auditing이 저장 시각으로 채우므로, 시각을 정해 넣으려고 native INSERT를 쓴다. */
    private void insertHistory(User user, String reason, LocalDateTime createdAt, boolean deleted) {
        entityManager.createNativeQuery("""
                        INSERT INTO user_nickname_histories
                            (id, user_id, nickname, reason, reserved_until, created_at, updated_at, is_deleted)
                        VALUES (:id, :userId, :nickname, :reason, :reservedUntil, :createdAt, :createdAt, :deleted)""")
                .setParameter("id", UUID.randomUUID())
                .setParameter("userId", user.getId())
                .setParameter("nickname", "예전닉")
                .setParameter("reason", reason)
                .setParameter("reservedUntil", createdAt.plusDays(14))
                .setParameter("createdAt", createdAt)
                .setParameter("deleted", deleted)
                .executeUpdate();
    }

    private User createUser(String nickname) {
        return User.create("김민트", "", Profile.of(nickname, Gender.FEMALE, LocalDate.of(1996, 4, 12), null),
                LocalDate.now());
    }
}
