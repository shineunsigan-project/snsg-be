package com.shineunsigan.snsg.admin.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.admin.domain.model.Admin;
import com.shineunsigan.snsg.admin.domain.model.AdminCursor;
import com.shineunsigan.snsg.common.config.JpaAuditingConfig;
import com.shineunsigan.snsg.common.exception.BusinessException;

import jakarta.persistence.EntityManager;

/**
 * 삭제되지 않은 행에만 걸리는 아이디 유니크 제약을 실제 PostgreSQL에서 본다.
 * 공유 DB라 테스트마다 무작위 아이디를 쓴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdminRepositoryImpl.class, JpaAuditingConfig.class})
class AdminRepositoryImplTest {

    @Autowired
    private AdminRepositoryImpl adminRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("같은 아이디로 삭제되지 않은 행을 둘 저장하면 500이 아니라 DUPLICATED_LOGIN_ID")
    void duplicatedLoginId() {
        String loginId = randomLoginId();
        adminRepository.save(admin(loginId));

        assertThatThrownBy(() -> adminRepository.save(admin(loginId)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AdminErrorCode.DUPLICATED_LOGIN_ID);
    }

    @Test
    @DisplayName("앞 행이 삭제됐으면 같은 아이디로 저장된다")
    void loginIdOfDeletedRowIsReusable() {
        String loginId = randomLoginId();
        Admin deleted = adminRepository.save(admin(loginId));
        deleted.delete();
        entityManager.flush();

        Admin saved = adminRepository.save(admin(loginId));

        assertThat(adminRepository.findByLoginId(loginId)).get().extracting(Admin::getId).isEqualTo(saved.getId());
        assertThat(adminRepository.existsByLoginId(loginId)).isTrue();
    }

    @Test
    @DisplayName("잠금 조회는 삭제된 행을 주지 않는다")
    void findByIdForUpdateSkipsDeleted() {
        Admin admin = adminRepository.save(admin(randomLoginId()));
        assertThat(adminRepository.findByIdForUpdate(admin.getId())).isPresent();

        admin.delete();
        entityManager.flush();
        entityManager.clear();

        assertThat(adminRepository.findByIdForUpdate(admin.getId())).isEmpty();
    }

    /**
     * 공유 DB라 다른 행이 섞인다. 과거 고정 시각(2020-01-01 12:00 근처)으로 native INSERT하고 그 직전 커서로 읽어,
     * 넣은 행이 들어 있는지와 넣은 행끼리의 순서만 본다. 실제 가입 행은 시각이 더 늦어 넣은 행보다 뒤에 온다.
     */
    @Nested
    @DisplayName("목록")
    class FindAll {

        private static final LocalDateTime BASE = LocalDateTime.of(2020, 1, 1, 12, 0);
        /** BASE 직전. 이 커서 뒤부터 읽으면 넣은 행이 맨 앞에 온다. */
        private static final AdminCursor BEFORE_BASE =
                new AdminCursor(BASE.minusMinutes(1), UUID.fromString("00000000-0000-0000-0000-000000000000"));
        private static final int ENOUGH = 1000;

        @Test
        @DisplayName("상태를 주면 그 상태만, 생략하면 모든 상태가 나온다. 삭제된 행은 나오지 않는다")
        void statusFilter() {
            UUID pending = insert(AdminStatus.PENDING, BASE, false);
            UUID approved = insert(AdminStatus.APPROVED, BASE.plusMinutes(1), false);
            UUID rejected = insert(AdminStatus.REJECTED, BASE.plusMinutes(2), false);
            UUID suspended = insert(AdminStatus.SUSPENDED, BASE.plusMinutes(3), false);
            UUID deleted = insert(AdminStatus.PENDING, BASE.plusMinutes(4), true);
            List<UUID> inserted = List.of(pending, approved, rejected, suspended, deleted);

            assertThat(ids(null, BEFORE_BASE, ENOUGH, inserted))
                    .containsExactly(pending, approved, rejected, suspended);
            assertThat(ids(AdminStatus.PENDING, BEFORE_BASE, ENOUGH, inserted)).containsExactly(pending);
            assertThat(ids(AdminStatus.APPROVED, BEFORE_BASE, ENOUGH, inserted)).containsExactly(approved);
            assertThat(ids(AdminStatus.REJECTED, BEFORE_BASE, ENOUGH, inserted)).containsExactly(rejected);
            assertThat(ids(AdminStatus.SUSPENDED, BEFORE_BASE, ENOUGH, inserted)).containsExactly(suspended);
        }

        @Test
        @DisplayName("(가입 신청 시각, id) 오름차순이고 커서보다 뒤만 나온다. 시각이 같으면 id로 가른다")
        void orderAndCursor() {
            // 앞 15바이트가 같고 마지막 자리만 다른 id. PostgreSQL uuid 순서는 바이트 순서라 ...1 < ...2다.
            String prefix = UUID.randomUUID().toString().substring(0, 35);
            UUID sameTimeFirst = UUID.fromString(prefix + "1");
            UUID sameTimeSecond = UUID.fromString(prefix + "2");
            UUID later = insert(AdminStatus.PENDING, BASE.plusMinutes(1), false);
            insertWithId(sameTimeSecond, BASE);
            insertWithId(sameTimeFirst, BASE);
            List<UUID> inserted = List.of(sameTimeFirst, sameTimeSecond, later);

            assertThat(ids(null, BEFORE_BASE, ENOUGH, inserted)).containsExactly(sameTimeFirst, sameTimeSecond, later);
            assertThat(ids(null, new AdminCursor(BASE, sameTimeFirst), ENOUGH, inserted))
                    .containsExactly(sameTimeSecond, later);
            assertThat(ids(null, new AdminCursor(BASE, sameTimeSecond), ENOUGH, inserted)).containsExactly(later);
        }

        @Test
        @DisplayName("limit건까지만 읽는다. 서비스가 size + 1을 넘겨 다음 페이지 유무를 정한다")
        void limit() {
            UUID first = insert(AdminStatus.PENDING, BASE, false);
            UUID second = insert(AdminStatus.PENDING, BASE.plusMinutes(1), false);
            insert(AdminStatus.PENDING, BASE.plusMinutes(2), false);

            assertThat(adminRepository.findAll(null, BEFORE_BASE, 2)).extracting(Admin::getId)
                    .containsExactly(first, second);
        }

        private List<UUID> ids(AdminStatus status, AdminCursor after, int limit, List<UUID> inserted) {
            return adminRepository.findAll(status, after, limit).stream()
                    .map(Admin::getId)
                    .filter(inserted::contains)
                    .toList();
        }

        private UUID insert(AdminStatus status, LocalDateTime createdAt, boolean deleted) {
            UUID id = UUID.randomUUID();
            insertRow(id, status, createdAt, deleted);
            return id;
        }

        private void insertWithId(UUID id, LocalDateTime createdAt) {
            insertRow(id, AdminStatus.PENDING, createdAt, false);
        }

        /** created_at을 과거로 두려면 native INSERT가 필요하다(@CreatedDate가 덮는다). 해시는 해시가 아닌 문자열이다. */
        private void insertRow(UUID id, AdminStatus status, LocalDateTime createdAt, boolean deleted) {
            entityManager.createNativeQuery("""
                            INSERT INTO admins (id, login_id, password_hash, name, team, birth_date, role, status,
                                                created_at, updated_at, created_by, updated_by, is_deleted)
                            VALUES (?, ?, 'stored-hash', '김민트', 'BACKEND', DATE '1996-04-12', 'ADMIN', ?,
                                    ?, ?, 'SYSTEM', 'SYSTEM', ?)""")
                    .setParameter(1, id)
                    .setParameter(2, randomLoginId())
                    .setParameter(3, status.name())
                    .setParameter(4, createdAt)
                    .setParameter(5, createdAt)
                    .setParameter(6, deleted)
                    .executeUpdate();
        }
    }

    private Admin admin(String loginId) {
        return Admin.signUp(loginId, "hashed-password", "김민트", AdminTeam.BACKEND, LocalDate.of(1996, 4, 12),
                LocalDate.now());
    }

    /** 영문 소문자로 시작하는 20자. 다른 테스트·워크트리의 행과 겹치지 않게 한다. */
    private String randomLoginId() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
    }
}
