package com.shineunsigan.snsg.admin.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.admin.domain.model.Admin;
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

    private Admin admin(String loginId) {
        return Admin.signUp(loginId, "hashed-password", "김민트", AdminTeam.BACKEND, LocalDate.of(1996, 4, 12),
                LocalDate.now());
    }

    /** 영문 소문자로 시작하는 20자. 다른 테스트·워크트리의 행과 겹치지 않게 한다. */
    private String randomLoginId() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
    }
}
