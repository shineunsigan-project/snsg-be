package com.shineunsigan.snsg.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.shineunsigan.snsg.common.config.JpaAuditingConfig;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.model.UserSocialAccount;

import jakarta.persistence.EntityManager;

/**
 * 사용자별 조회가 삭제된 행을 빼는지 본다. 전역 필터 대신 쿼리마다 조건을 명시하므로 쿼리별로 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserRepositoryImpl.class, UserSocialAccountRepositoryImpl.class, JpaAuditingConfig.class})
class UserSocialAccountRepositoryImplTest {

    @Autowired
    private UserRepositoryImpl userRepository;

    @Autowired
    private UserSocialAccountRepositoryImpl userSocialAccountRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("이미 삭제된 소셜 계정은 사용자별 조회에서 빠진다")
    void deletedAccountIsNotFound() {
        User user = userRepository.save(User.create("김민트", "",
                Profile.of("민트초코", Gender.FEMALE, LocalDate.of(1996, 4, 12), "/images/default-profile.png"),
                LocalDate.now()));
        UserSocialAccount account = userSocialAccountRepository
                .save(UserSocialAccount.of(user, SocialProvider.KAKAO, "kakao-1"));
        entityManager.flush();

        assertThat(userSocialAccountRepository.findByUserId(user.getId())).containsExactly(account);

        account.delete();
        entityManager.flush();

        assertThat(userSocialAccountRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("비식별로 식별자를 비운 삭제된 소셜 계정은 빈 식별자로 찾아도 나오지 않는다")
    void anonymizedAccountIsNotFoundByEmptyProviderUserId() {
        User user = userRepository.save(User.create("김민트", "",
                Profile.of("t" + UUID.randomUUID().toString().substring(0, 8), Gender.FEMALE,
                        LocalDate.of(1996, 4, 12), null),
                LocalDate.now()));
        UserSocialAccount account = userSocialAccountRepository
                .save(UserSocialAccount.of(user, SocialProvider.KAKAO, "kakao-" + UUID.randomUUID()));
        entityManager.flush();

        account.delete();
        account.clearProviderUserId();
        entityManager.flush();

        Object[] stored = (Object[]) entityManager.createNativeQuery(
                        "SELECT provider_user_id, is_deleted FROM user_social_accounts WHERE id = ?")
                .setParameter(1, account.getId())
                .getSingleResult();
        assertThat(stored).containsExactly("", true);
        assertThat(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "")).isEmpty();
    }
}
