package com.shineunsigan.snsg.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    /** 이력의 보호 기간을 이 시각 앞뒤로 넣어 시계에 흔들리지 않게 한다. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 12, 0);

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

        assertThat(userRepository.isNicknameTaken("민트초코", NOW)).isTrue();
    }

    // 탈퇴 닉네임 보호는 users 행이 아니라 탈퇴 서비스가 남기는 WITHDRAWN 이력이 맡는다.
    @Test
    @DisplayName("이력이 없으면 삭제된 사용자 행의 닉네임은 중복 판정과 닉네임 부분 유니크 인덱스에 걸리지 않는다")
    void nicknameOfDeletedUserRowIsIgnoredWithoutHistory() {
        User user = userRepository.save(createUser("민트초코"));
        user.withdraw();
        entityManager.flush();

        assertThat(userRepository.isNicknameTaken("민트초코", NOW)).isFalse();
        userRepository.save(createUser("민트초코"));
        entityManager.flush();
    }

    @Test
    @DisplayName("대소문자만 다른 닉네임도 중복으로 본다")
    void duplicatedNicknameIgnoresCase() {
        userRepository.save(createUser("Mint"));

        assertThat(userRepository.isNicknameTaken("mint", NOW)).isTrue();
        assertThat(userRepository.isNicknameTaken("MINT", NOW)).isTrue();
        assertThat(userRepository.isNicknameTaken("mint2", NOW)).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 사용자의 닉네임은 대소문자가 달라도 중복으로 보지 않는다")
    void nicknameOfWithdrawnUserIsNotDuplicatedIgnoringCase() {
        User user = userRepository.save(createUser("Mint"));
        user.withdraw();
        entityManager.flush();

        assertThat(userRepository.isNicknameTaken("mint", NOW)).isFalse();
    }

    @Test
    @DisplayName("보호 기간이 남은 변경 이력의 닉네임은 대소문자가 달라도 중복으로 본다")
    void nicknameProtectedAfterChange() {
        User user = userRepository.save(createUser("Lemon"));
        insertHistory(user, "Mint", "CHANGED", NOW.plusDays(1), false);

        assertThat(userRepository.isNicknameTaken("Mint", NOW)).isTrue();
        assertThat(userRepository.isNicknameTaken("mint", NOW)).isTrue();
    }

    @Test
    @DisplayName("보호 기간이 남은 탈퇴 이력의 닉네임은 중복으로 본다")
    void nicknameProtectedAfterWithdrawal() {
        User user = userRepository.save(createUser("민트초코"));
        user.withdraw();
        entityManager.flush();
        insertHistory(user, "민트초코", "WITHDRAWN", NOW.plusDays(90), false);

        assertThat(userRepository.isNicknameTaken("민트초코", NOW)).isTrue();
    }

    @Test
    @DisplayName("보호 기간이 지난 이력의 닉네임은 다시 쓸 수 있다")
    void nicknameReusableAfterProtection() {
        User user = userRepository.save(createUser("Lemon"));
        insertHistory(user, "Mint", "CHANGED", NOW.minusSeconds(1), false);

        assertThat(userRepository.isNicknameTaken("Mint", NOW)).isFalse();
    }

    @Test
    @DisplayName("삭제된 이력은 보호 기간이 남아도 보지 않는다")
    void deletedHistoryIsIgnored() {
        User user = userRepository.save(createUser("Lemon"));
        insertHistory(user, "Mint", "CHANGED", NOW.plusDays(1), true);

        assertThat(userRepository.isNicknameTaken("Mint", NOW)).isFalse();
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

    @Test
    @DisplayName("탈퇴하지 않은 다른 회원이 쓰는 사진 경로면 사용 중이다")
    void profileImageUsedByOthers() {
        String path = uploadedPath();
        userRepository.save(createUser("민트초코", path));
        User me = userRepository.save(createUser("레몬티"));

        assertThat(userRepository.isProfileImageUsedByOthers(path, me.getId())).isTrue();
    }

    @Test
    @DisplayName("그 사진 경로를 쓰는 회원이 본인뿐이면 사용 중이 아니다")
    void profileImageUsedOnlyByMe() {
        String path = uploadedPath();
        User me = userRepository.save(createUser("민트초코", path));

        assertThat(userRepository.isProfileImageUsedByOthers(path, me.getId())).isFalse();
    }

    @Test
    @DisplayName("그 사진 경로를 쓰던 회원이 탈퇴(삭제)했어도 비식별 전이면 사용 중이다")
    void profileImageOfDeletedUserIsStillUsed() {
        String path = uploadedPath();
        User other = userRepository.save(createUser("민트초코", path));
        other.delete();
        entityManager.flush();
        User me = userRepository.save(createUser("레몬티"));

        assertThat(userRepository.isProfileImageUsedByOthers(path, me.getId())).isTrue();
        assertThat(userRepository.isProfileImageUsedByOthers(path, null)).isTrue();
    }

    @Test
    @DisplayName("가입(본인 없음)에서는 누군가 쓰면 사용 중, 아무도 안 쓰면 사용 중이 아니다")
    void profileImageUsedOnSignUp() {
        String used = uploadedPath();
        userRepository.save(createUser("민트초코", used));

        assertThat(userRepository.isProfileImageUsedByOthers(used, null)).isTrue();
        assertThat(userRepository.isProfileImageUsedByOthers(uploadedPath(), null)).isFalse();
    }

    @Test
    @DisplayName("삭제 여부와 상관없이 ID들을 한 번에 읽고, 없는 ID는 결과에 없다")
    void findAllByIdIncludingDeleted() {
        User active = userRepository.save(createUser(randomNickname()));
        User deleted = userRepository.save(createUser(randomNickname()));
        deleted.delete();
        entityManager.flush();
        entityManager.clear();

        List<User> found = userRepository.findAllByIdIncludingDeleted(
                List.of(active.getId(), deleted.getId(), UUID.randomUUID()));

        assertThat(found).extracting(User::getId).containsExactlyInAnyOrder(active.getId(), deleted.getId());
        assertThat(found).filteredOn(user -> user.getId().equals(deleted.getId()))
                .singleElement()
                .extracting(User::isDeleted)
                .isEqualTo(true);
    }

    /** 공유 DB의 다른 행과 닉네임이 겹치지 않게 무작위로 만든다. */
    private String randomNickname() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    }

    private String uploadedPath() {
        return "/images/profiles/" + UUID.randomUUID() + ".png";
    }

    private User createUser(String nickname, String profileImageUrl) {
        return User.create("김민트", "",
                Profile.of(nickname, Gender.FEMALE, LocalDate.of(1996, 4, 12), profileImageUrl), LocalDate.now());
    }

    /** 이력 엔티티가 아직 없어(T-03에서 만든다) native INSERT로 넣는다. */
    private void insertHistory(User user, String nickname, String reason, LocalDateTime reservedUntil,
            boolean deleted) {
        entityManager.createNativeQuery("""
                        INSERT INTO user_nickname_histories
                            (id, user_id, nickname, reason, reserved_until, created_at, updated_at, is_deleted)
                        VALUES (:id, :userId, :nickname, :reason, :reservedUntil, :now, :now, :deleted)""")
                .setParameter("id", UUID.randomUUID())
                .setParameter("userId", user.getId())
                .setParameter("nickname", nickname)
                .setParameter("reason", reason)
                .setParameter("reservedUntil", reservedUntil)
                .setParameter("now", NOW)
                .setParameter("deleted", deleted)
                .executeUpdate();
    }

    private User createUser(String nickname) {
        return User.create("김민트", "", Profile.of(nickname, Gender.FEMALE, LocalDate.of(1996, 4, 12), null),
                LocalDate.now());
    }
}
