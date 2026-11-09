package com.shineunsigan.snsg.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.shineunsigan.snsg.common.config.JpaAuditingConfig;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;
import com.shineunsigan.snsg.user.domain.model.AnonymizeCursor;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.model.UserWithdrawal;

import jakarta.persistence.EntityManager;

/**
 * 비식별 대상 조회의 조건(아직 비식별 안 함, 예정 시각 경계)과 keyset 페이지를 실제 PostgreSQL로 본다.
 * <p>
 * DB를 모든 워크트리가 공유하므로 기준 시각을 과거 고정 값으로 두고, 넣은 행의 사용자 id로만 단정한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserRepositoryImpl.class, UserWithdrawalRepositoryImpl.class, JpaAuditingConfig.class})
class UserWithdrawalRepositoryImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2020, 1, 1, 12, 0);

    @Autowired
    private UserRepositoryImpl userRepository;

    @Autowired
    private UserWithdrawalRepositoryImpl userWithdrawalRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("예정 시각이 기준 시각과 같으면 대상이고, 1분 뒤면 대상이 아니다")
    void dueBoundary() {
        UUID due = withdraw(NOW, false);
        UUID notYet = withdraw(NOW.plusMinutes(1), false);

        List<UUID> found = targetIds(userWithdrawalRepository.findAnonymizeTargets(NOW, null, 1000));

        assertThat(found).contains(due).doesNotContain(notYet);
    }

    @Test
    @DisplayName("이미 비식별한 탈퇴 기록은 예정 시각이 지났어도 대상이 아니다")
    void anonymizedIsExcluded() {
        UUID anonymized = withdraw(NOW.minusDays(1), true);

        List<UUID> found = targetIds(userWithdrawalRepository.findAnonymizeTargets(NOW, null, 1000));

        assertThat(found).doesNotContain(anonymized);
    }

    @Test
    @DisplayName("커서 다음 페이지는 (예정 시각, 사용자 id) 순서로 이어지고, 같은 예정 시각은 사용자 id로 갈린다")
    void keysetPaging() {
        LocalDateTime base = LocalDateTime.of(2019, 6, 1, 4, 0);
        UUID first = withdraw(base, false);
        UUID tieA = withdraw(base.plusHours(1), false);
        UUID tieB = withdraw(base.plusHours(1), false);
        Set<UUID> inserted = Set.of(first, tieA, tieB);
        List<UUID> tieOrder = orderedByDb(tieA, tieB);

        List<UserWithdrawal> firstPage = userWithdrawalRepository.findAnonymizeTargets(NOW, null, 2);
        UserWithdrawal last = firstPage.get(firstPage.size() - 1);
        List<UserWithdrawal> secondPage = userWithdrawalRepository.findAnonymizeTargets(NOW,
                new AnonymizeCursor(last.getAnonymizeDueAt(), last.getUser().getId()), 2);

        List<UUID> firstIds = targetIds(firstPage);
        List<UUID> secondIds = targetIds(secondPage);
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
        List<UUID> all = new ArrayList<>(firstIds);
        all.addAll(secondIds);
        assertThat(all.stream().filter(inserted::contains).toList())
                .containsExactly(first, tieOrder.get(0), tieOrder.get(1));
    }

    /** 탈퇴 기록을 넣는다. 보관 기간 0으로 두어 예정 시각이 {@code dueAt}이 된다. */
    private UUID withdraw(LocalDateTime dueAt, boolean anonymized) {
        User user = userRepository.save(User.create("김민트", "",
                Profile.of("t" + UUID.randomUUID().toString().replace("-", "").substring(0, 9), Gender.FEMALE,
                        LocalDate.of(1996, 4, 12), null),
                LocalDate.now()));
        user.withdraw();
        UserWithdrawal withdrawal = UserWithdrawal.of(user, WithdrawReason.USER_REQUESTED, dueAt, Duration.ZERO);
        if (anonymized) {
            withdrawal.markAnonymized();
        }
        userWithdrawalRepository.save(withdrawal);
        entityManager.flush();
        return user.getId();
    }

    /** PostgreSQL uuid 순서. Java의 UUID 비교는 부호 있는 비교라 다르다. */
    @SuppressWarnings("unchecked")
    private List<UUID> orderedByDb(UUID a, UUID b) {
        return entityManager.createNativeQuery("SELECT id FROM users WHERE id IN (?, ?) ORDER BY id")
                .setParameter(1, a)
                .setParameter(2, b)
                .getResultList();
    }

    private List<UUID> targetIds(List<UserWithdrawal> withdrawals) {
        return withdrawals.stream().map(withdrawal -> withdrawal.getUser().getId()).toList();
    }
}
