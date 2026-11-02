package com.shineunsigan.snsg.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.shineunsigan.snsg.common.config.JpaAuditingConfig;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.model.UserSocialAccount;

import jakarta.persistence.EntityManager;

/**
 * 삭제되지 않은 행에만 걸리는 유니크 제약을 검증한다.
 * 이 제약은 PostgreSQL 부분 인덱스라서 임베디드 DB로 바꿔치기하면 아예 만들어지지 않는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserRepositoryImpl.class, UserSocialAccountRepositoryImpl.class, JpaAuditingConfig.class})
class UserRepositoryImplTest {

    @Autowired
    private UserRepositoryImpl userRepository;

    @Autowired
    private UserSocialAccountRepositoryImpl userSocialAccountRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("같은 닉네임을 쓰는 사용자가 이미 있으면 중복으로 본다")
    void duplicatedNickname() {
        userRepository.save(createUser("민트초코"));
        entityManager.flush();

        assertThat(userRepository.existsByNickname("민트초코")).isTrue();
    }

    @Test
    @DisplayName("탈퇴한 사용자의 닉네임은 다시 쓸 수 있다")
    void nicknameOfWithdrawnUserIsReusable() {
        User user = userRepository.save(createUser("민트초코"));
        user.withdraw();
        entityManager.flush();

        assertThat(userRepository.existsByNickname("민트초코")).isFalse();
        userRepository.save(createUser("민트초코"));
        entityManager.flush();
    }

    @Test
    @DisplayName("대소문자만 다른 닉네임도 중복으로 본다")
    void duplicatedNicknameIgnoresCase() {
        userRepository.save(createUser("Mint"));

        assertThat(userRepository.existsByNickname("mint")).isTrue();
        assertThat(userRepository.existsByNickname("MINT")).isTrue();
        assertThat(userRepository.existsByNickname("mint2")).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 사용자의 닉네임은 대소문자가 달라도 중복으로 보지 않는다")
    void nicknameOfWithdrawnUserIsNotDuplicatedIgnoringCase() {
        User user = userRepository.save(createUser("Mint"));
        user.withdraw();
        entityManager.flush();

        assertThat(userRepository.existsByNickname("mint")).isFalse();
    }

    @Test
    @DisplayName("중복 판정 없이 대소문자만 다른 닉네임을 저장하면 인덱스가 막는다")
    void nicknameIndexIgnoresCase() {
        entityManager.persist(createUser("Mint"));
        entityManager.flush();

        entityManager.persist(createUser("mint"));

        assertThatThrownBy(entityManager::flush)
                .rootCause()
                .hasMessageContaining("uix_users_nickname");
    }

    @Test
    @DisplayName("저장 중 닉네임 인덱스를 위반하면(동시 가입 경합) DUPLICATED_NICKNAME")
    void nicknameIndexViolationBecomesDuplicatedNickname() {
        userRepository.save(createUser("Mint"));

        assertThatThrownBy(() -> userRepository.save(createUser("mint")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.DUPLICATED_NICKNAME);
    }

    @Test
    @DisplayName("같은 소셜 계정으로 두 번 가입할 수 없다")
    void duplicatedSocialAccount() {
        User user = userRepository.save(createUser("민트초코"));
        userSocialAccountRepository.save(UserSocialAccount.of(user, SocialProvider.KAKAO, "kakao-1"));
        entityManager.flush();

        User another = userRepository.save(createUser("레몬티"));
        userSocialAccountRepository.save(UserSocialAccount.of(another, SocialProvider.KAKAO, "kakao-1"));

        assertThatThrownBy(entityManager::flush)
                .rootCause()
                .hasMessageContaining("uix_user_social_accounts_provider");
    }

    @Test
    @DisplayName("탈퇴 후 같은 소셜 계정으로 다시 가입하면 새 사용자가 된다")
    void rejoinCreatesNewUser() {
        User user = userRepository.save(createUser("민트초코"));
        UserSocialAccount account = userSocialAccountRepository
                .save(UserSocialAccount.of(user, SocialProvider.KAKAO, "kakao-1"));
        entityManager.flush();

        user.withdraw();
        account.delete();
        entityManager.flush();

        User rejoined = userRepository.save(createUser("민트초코"));
        userSocialAccountRepository.save(UserSocialAccount.of(rejoined, SocialProvider.KAKAO, "kakao-1"));
        entityManager.flush();

        assertThat(rejoined.getId()).isNotEqualTo(user.getId());
        assertThat(userSocialAccountRepository
                .findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-1"))
                .get()
                .extracting(found -> found.getUser().getId())
                .isEqualTo(rejoined.getId());
    }

    @Test
    @DisplayName("인증 주체가 없으면 감사 컬럼에 SYSTEM이 들어간다")
    void auditColumnsFilledWithSystem() {
        User user = userRepository.save(createUser("민트초코"));
        entityManager.flush();

        assertThat(user.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(user.getUpdatedBy()).isEqualTo("SYSTEM");
        assertThat(user.getCreatedAt()).isNotNull();
    }

    private User createUser(String nickname) {
        return User.create("김민트", "", Profile.of(nickname, Gender.FEMALE, LocalDate.of(1996, 4, 12), null),
                LocalDate.now());
    }
}
