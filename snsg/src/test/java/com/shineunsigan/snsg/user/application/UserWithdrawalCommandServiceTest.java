package com.shineunsigan.snsg.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.shineunsigan.snsg.common.config.JpaAuditingConfig;
import com.shineunsigan.snsg.common.entity.Auditor;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.dto.result.AnonymizeTargetResult;
import com.shineunsigan.snsg.user.application.event.UserAnonymizedEvent;
import com.shineunsigan.snsg.user.application.event.UserWithdrawnEvent;
import com.shineunsigan.snsg.user.application.port.out.UserEventPort;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.NicknameHistoryReason;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.constants.TermsType;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.AnonymizeCursor;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.TermsAgreement;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.model.UserNicknameHistory;
import com.shineunsigan.snsg.user.domain.model.UserSocialAccount;
import com.shineunsigan.snsg.user.domain.model.UserWithdrawal;
import com.shineunsigan.snsg.user.domain.repository.TermsAgreementRepository;
import com.shineunsigan.snsg.user.domain.repository.UserNicknameHistoryRepository;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;
import com.shineunsigan.snsg.user.domain.repository.UserSocialAccountRepository;
import com.shineunsigan.snsg.user.domain.repository.UserWithdrawalRepository;
import com.shineunsigan.snsg.user.infrastructure.persistence.TermsAgreementRepositoryImpl;
import com.shineunsigan.snsg.user.infrastructure.persistence.UserNicknameHistoryRepositoryImpl;
import com.shineunsigan.snsg.user.infrastructure.persistence.UserRepositoryImpl;
import com.shineunsigan.snsg.user.infrastructure.persistence.UserSocialAccountRepositoryImpl;
import com.shineunsigan.snsg.user.infrastructure.persistence.UserWithdrawalRepositoryImpl;

import jakarta.persistence.EntityManager;

/**
 * 탈퇴가 애그리거트 전체를 정리하고 탈퇴 기록·닉네임 보호 이력을 남기는지, 비식별이 개인정보를 지우는지 본다.
 * <p>
 * 흐름은 Mockito로, 닉네임 보호·비식별 결과·감사 값은 실제 PostgreSQL로 본다.
 */
class UserWithdrawalCommandServiceTest {

    private static final Duration RETENTION = Duration.ofDays(90);
    private static final NicknameProperties NICKNAME_PROPERTIES = new NicknameProperties(Duration.ofDays(14), RETENTION);
    private static final WithdrawalProperties WITHDRAWAL_PROPERTIES = new WithdrawalProperties(100);
    private static final String UPLOADED_IMAGE = "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png";
    private static final String KAKAO_IMAGE = "https://k.kakaocdn.net/dn/abc/img_640x640.jpg";

    /** 공유 DB의 다른 행과 겹치지 않게 무작위로 만든다. 소문자 hex라 대문자로 바꾸면 다른 문자열이 된다. */
    private static String randomNickname() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    }

    private static User user(String nickname, String profileImageUrl) {
        return User.create("김민트", "mint@example.com",
                Profile.of(nickname, Gender.FEMALE, LocalDate.of(1996, 4, 12), profileImageUrl), LocalDate.now());
    }

    @Nested
    @DisplayName("탈퇴·비식별 흐름")
    @ExtendWith(MockitoExtension.class)
    class Flow {

        private static final String PROVIDER_USER_ID = "kakao-1";

        @Mock
        private UserRepository userRepository;

        @Mock
        private UserSocialAccountRepository userSocialAccountRepository;

        @Mock
        private TermsAgreementRepository termsAgreementRepository;

        @Mock
        private UserNicknameHistoryRepository userNicknameHistoryRepository;

        @Mock
        private UserWithdrawalRepository userWithdrawalRepository;

        @Mock
        private UserEventPort userEventPort;

        private UserWithdrawalCommandService service;

        @BeforeEach
        void setUp() {
            service = new UserWithdrawalCommandService(userRepository, userSocialAccountRepository,
                    termsAgreementRepository, userNicknameHistoryRepository, userWithdrawalRepository, userEventPort,
                    NICKNAME_PROPERTIES, WITHDRAWAL_PROPERTIES);
        }

        private void stubWithdrawable(UUID userId, User user, List<UserSocialAccount> accounts) {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userSocialAccountRepository.findByUserId(userId)).thenReturn(accounts);
            when(termsAgreementRepository.findByUserId(userId)).thenReturn(List.of());
        }

        @Test
        @DisplayName("탈퇴하면 소셜 계정과 약관 동의도 함께 삭제된다")
        void withdrawDeletesWholeAggregate() {
            User user = user("민트초코", UPLOADED_IMAGE);
            UUID userId = user.getId();
            UserSocialAccount socialAccount = UserSocialAccount.of(user, SocialProvider.KAKAO, PROVIDER_USER_ID);
            TermsAgreement termsAgreement = TermsAgreement.of(user, TermsType.SERVICE_TERMS, "2026-10-01");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userSocialAccountRepository.findByUserId(userId)).thenReturn(List.of(socialAccount));
            when(termsAgreementRepository.findByUserId(userId)).thenReturn(List.of(termsAgreement));

            service.withdraw(userId, WithdrawReason.USER_REQUESTED);

            assertThat(user.isDeleted()).isTrue();
            assertThat(socialAccount.isDeleted()).isTrue();
            assertThat(termsAgreement.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("탈퇴 이벤트를 소셜 계정마다 사용자·소셜 계정·사유를 담아 낸다")
        void withdrawPublishesEvent() {
            UUID userId = UUID.randomUUID();
            User user = user("민트초코", UPLOADED_IMAGE);
            stubWithdrawable(userId, user, List.of(UserSocialAccount.of(user, SocialProvider.KAKAO, PROVIDER_USER_ID)));

            service.withdraw(userId, WithdrawReason.USER_REQUESTED);

            ArgumentCaptor<UserWithdrawnEvent> captor = ArgumentCaptor.forClass(UserWithdrawnEvent.class);
            verify(userEventPort).publish(captor.capture());
            UserWithdrawnEvent event = captor.getValue();
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.provider()).isEqualTo(SocialProvider.KAKAO);
            assertThat(event.providerUserId()).isEqualTo(PROVIDER_USER_ID);
            assertThat(event.reason()).isEqualTo(WithdrawReason.USER_REQUESTED);
        }

        @Test
        @DisplayName("본인 요청 탈퇴는 사유 USER_REQUESTED인 탈퇴 기록과 WITHDRAWN 닉네임 이력을 같은 기한으로 남긴다")
        void withdrawRecordsWithdrawalAndHistory() {
            UUID userId = UUID.randomUUID();
            stubWithdrawable(userId, user("민트초코", UPLOADED_IMAGE), List.of());
            LocalDateTime before = LocalDateTime.now();

            service.withdraw(userId, WithdrawReason.USER_REQUESTED);

            assertRecorded(WithdrawReason.USER_REQUESTED, before, LocalDateTime.now());
        }

        @Test
        @DisplayName("소셜 연결 끊기 탈퇴는 사유 SOCIAL_UNLINKED인 탈퇴 기록과 WITHDRAWN 닉네임 이력을 같은 기한으로 남긴다")
        void withdrawBySocialAccountRecordsWithdrawalAndHistory() {
            User user = user("민트초코", UPLOADED_IMAGE);
            UUID userId = user.getId();
            UserSocialAccount account = UserSocialAccount.of(user, SocialProvider.KAKAO, PROVIDER_USER_ID);
            when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, PROVIDER_USER_ID))
                    .thenReturn(Optional.of(account));
            stubWithdrawable(userId, user, List.of(account));
            LocalDateTime before = LocalDateTime.now();

            service.withdrawBySocialAccount(SocialProvider.KAKAO, PROVIDER_USER_ID);

            assertRecorded(WithdrawReason.SOCIAL_UNLINKED, before, LocalDateTime.now());
        }

        private void assertRecorded(WithdrawReason reason, LocalDateTime before, LocalDateTime after) {
            ArgumentCaptor<UserWithdrawal> withdrawal = ArgumentCaptor.forClass(UserWithdrawal.class);
            verify(userWithdrawalRepository).save(withdrawal.capture());
            ArgumentCaptor<UserNicknameHistory> history = ArgumentCaptor.forClass(UserNicknameHistory.class);
            verify(userNicknameHistoryRepository).save(history.capture());

            assertThat(withdrawal.getValue().getReason()).isEqualTo(reason);
            assertThat(withdrawal.getValue().isAnonymized()).isFalse();
            assertThat(history.getValue().getReason()).isEqualTo(NicknameHistoryReason.WITHDRAWN);
            assertThat(history.getValue().getNickname()).isEqualTo("민트초코");
            assertThat(withdrawal.getValue().getAnonymizeDueAt()).isEqualTo(history.getValue().getReservedUntil());
            assertThat(withdrawal.getValue().getAnonymizeDueAt())
                    .isBetween(before.plus(RETENTION), after.plus(RETENTION));
        }

        @Test
        @DisplayName("없는 사용자를 탈퇴시키면 USER_NOT_FOUND")
        void withdrawUnknownUser() {
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.withdraw(userId, WithdrawReason.USER_REQUESTED))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("가입하지 않은 소셜 계정의 연결 끊기 통지는 그냥 넘어간다")
        void withdrawBySocialAccountIgnoresUnknownAccount() {
            when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, PROVIDER_USER_ID))
                    .thenReturn(Optional.empty());

            service.withdrawBySocialAccount(SocialProvider.KAKAO, PROVIDER_USER_ID);

            verify(userEventPort, never()).publish(any());
            verify(userWithdrawalRepository, never()).save(any());
        }

        private UserWithdrawal withdrawnRecord(User user) {
            user.withdraw();
            return UserWithdrawal.of(user, WithdrawReason.USER_REQUESTED, LocalDateTime.of(2020, 1, 1, 0, 0),
                    RETENTION);
        }

        @Test
        @DisplayName("비식별하면 회원·닉네임 이력·소셜 계정의 개인정보를 지우고 탈퇴 기록에 표시하며, 업로드 사진이면 이전 경로로 이벤트를 한 번 낸다")
        void anonymizeUploadedImageUser() {
            UUID userId = UUID.randomUUID();
            User user = user("민트초코", UPLOADED_IMAGE);
            UserWithdrawal withdrawal = withdrawnRecord(user);
            UserNicknameHistory history = UserNicknameHistory.changed(user, "레몬티", LocalDateTime.now(),
                    Duration.ofDays(14));
            UserSocialAccount account = UserSocialAccount.of(user, SocialProvider.KAKAO, PROVIDER_USER_ID);
            when(userWithdrawalRepository.findByUserId(userId)).thenReturn(Optional.of(withdrawal));
            when(userNicknameHistoryRepository.findAllByUserIdIncludingDeleted(userId)).thenReturn(List.of(history));
            when(userSocialAccountRepository.findAllByUserIdIncludingDeleted(userId)).thenReturn(List.of(account));

            service.anonymize(userId);

            assertThat(user.getName()).isEmpty();
            assertThat(user.getProfile().getNickname()).isEqualTo(Profile.WITHDRAWN_NICKNAME);
            assertThat(history.getNickname()).isEmpty();
            assertThat(account.getProviderUserId()).isEmpty();
            assertThat(withdrawal.isAnonymized()).isTrue();
            ArgumentCaptor<UserAnonymizedEvent> captor = ArgumentCaptor.forClass(UserAnonymizedEvent.class);
            verify(userEventPort).publishUserAnonymized(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(userId);
            assertThat(captor.getValue().profileImageUrl()).isEqualTo(UPLOADED_IMAGE);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {KAKAO_IMAGE})
        @DisplayName("기본 이미지·카카오 사진 회원은 비식별해도 사진 삭제 이벤트를 내지 않는다")
        void anonymizeNonUploadedImageUser(String profileImageUrl) {
            UUID userId = UUID.randomUUID();
            User user = user("민트초코", profileImageUrl);
            UserWithdrawal withdrawal = withdrawnRecord(user);
            when(userWithdrawalRepository.findByUserId(userId)).thenReturn(Optional.of(withdrawal));

            service.anonymize(userId);

            assertThat(withdrawal.isAnonymized()).isTrue();
            verify(userEventPort, never()).publishUserAnonymized(any());
        }

        @Test
        @DisplayName("이미 비식별한 회원이면 아무것도 바꾸지 않고 이벤트도 내지 않는다")
        void anonymizeAlreadyAnonymized() {
            UUID userId = UUID.randomUUID();
            User user = user("민트초코", UPLOADED_IMAGE);
            UserWithdrawal withdrawal = withdrawnRecord(user);
            withdrawal.markAnonymized();
            when(userWithdrawalRepository.findByUserId(userId)).thenReturn(Optional.of(withdrawal));

            service.anonymize(userId);

            assertThat(user.getName()).isEqualTo("김민트");
            verifyNoInteractions(userNicknameHistoryRepository, userSocialAccountRepository);
            verify(userEventPort, never()).publishUserAnonymized(any());
        }

        @Test
        @DisplayName("탈퇴 기록이 없는(탈퇴하지 않은) 회원이면 예외 없이 아무것도 바꾸지 않는다")
        void anonymizeNotWithdrawnUser() {
            UUID userId = UUID.randomUUID();
            when(userWithdrawalRepository.findByUserId(userId)).thenReturn(Optional.empty());

            service.anonymize(userId);

            verifyNoInteractions(userRepository, userNicknameHistoryRepository, userSocialAccountRepository,
                    userEventPort);
        }

        @Test
        @DisplayName("대상 조회는 앞 페이지 마지막 대상을 커서로 넘기고 설정의 페이지 크기로 읽는다")
        void findAnonymizeTargetsPassesCursorAndPageSize() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 1, 4, 0);
            AnonymizeTargetResult after = new AnonymizeTargetResult(UUID.randomUUID(), now.minusDays(1));

            service.findAnonymizeTargets(now, after);

            verify(userWithdrawalRepository).findAnonymizeTargets(now,
                    new AnonymizeCursor(after.anonymizeDueAt(), after.userId()), 100);
        }
    }

    @TestConfiguration
    static class PropertiesConfig {

        @Bean
        NicknameProperties nicknameProperties() {
            return NICKNAME_PROPERTIES;
        }

        @Bean
        WithdrawalProperties withdrawalProperties() {
            return WITHDRAWAL_PROPERTIES;
        }
    }

    /**
     * 테스트 트랜잭션 안에서 서비스를 부르고 flush한 뒤 native SELECT로 저장된 값을 읽는다. 커밋하지 않는다.
     */
    @Nested
    @DisplayName("저장 결과")
    @DataJpaTest
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    @Import({UserWithdrawalCommandService.class, UserRepositoryImpl.class, UserSocialAccountRepositoryImpl.class,
            TermsAgreementRepositoryImpl.class, UserNicknameHistoryRepositoryImpl.class,
            UserWithdrawalRepositoryImpl.class, JpaAuditingConfig.class, PropertiesConfig.class})
    class Stored {

        @Autowired
        private UserWithdrawalCommandService service;

        @Autowired
        private UserRepositoryImpl userRepository;

        @Autowired
        private UserSocialAccountRepositoryImpl userSocialAccountRepository;

        @Autowired
        private UserNicknameHistoryRepositoryImpl userNicknameHistoryRepository;

        @Autowired
        private EntityManager entityManager;

        @MockitoBean
        private UserEventPort userEventPort;

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        private void authenticate(Object principal) {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        }

        /** 사진·소셜 계정·닉네임 변경 이력을 가진 회원을 만들고 탈퇴시킨 뒤, 탈퇴 기록의 예정 시각을 과거로 돌린다. */
        private User withdrawnUser(String nickname) {
            User user = userRepository.save(user(nickname, "/images/profiles/" + UUID.randomUUID() + ".png"));
            userSocialAccountRepository.save(
                    UserSocialAccount.of(user, SocialProvider.KAKAO, "kakao-" + UUID.randomUUID()));
            userNicknameHistoryRepository.save(UserNicknameHistory.changed(user, randomNickname(),
                    LocalDateTime.now(), Duration.ofDays(14)));
            entityManager.flush();

            service.withdraw(user.getId(), WithdrawReason.USER_REQUESTED);
            entityManager.flush();
            entityManager.createNativeQuery(
                            "UPDATE user_withdrawals SET anonymize_due_at = '2020-01-01 12:00' WHERE user_id = ?")
                    .setParameter(1, user.getId())
                    .executeUpdate();
            entityManager.clear();
            return user;
        }

        private Object[] row(String sql, UUID userId) {
            return (Object[]) entityManager.createNativeQuery(sql).setParameter(1, userId).getSingleResult();
        }

        @SuppressWarnings("unchecked")
        private List<Object> column(String sql, UUID userId) {
            return entityManager.createNativeQuery(sql).setParameter(1, userId).getResultList();
        }

        @Test
        @DisplayName("탈퇴한 회원의 닉네임은 대소문자가 달라도 보호 기간 동안 쓸 수 없고, 보호 기간이 끝나면 쓸 수 있다")
        void nicknameProtectedAfterWithdrawal() {
            String nickname = randomNickname();
            User user = userRepository.save(user(nickname, null));
            entityManager.flush();

            service.withdraw(user.getId(), WithdrawReason.USER_REQUESTED);
            entityManager.flush();

            Object reservedUntil = entityManager.createNativeQuery("""
                            SELECT reserved_until FROM user_nickname_histories
                            WHERE user_id = ? AND reason = 'WITHDRAWN'""")
                    .setParameter(1, user.getId())
                    .getSingleResult();
            LocalDateTime protectedUntil = reservedUntil instanceof java.sql.Timestamp timestamp
                    ? timestamp.toLocalDateTime() : (LocalDateTime) reservedUntil;
            assertThat(userRepository.isNicknameTaken(nickname, LocalDateTime.now())).isTrue();
            assertThat(userRepository.isNicknameTaken(nickname.toUpperCase(), LocalDateTime.now())).isTrue();
            assertThat(userRepository.isNicknameTaken(nickname, protectedUntil)).isFalse();
        }

        @Test
        @DisplayName("비식별하면 회원·모든 닉네임 이력·모든 소셜 계정의 개인정보가 지워지고 탈퇴 기록이 표시되며, 다시 불러도 바뀌지 않는다")
        void anonymizeClearsPersonalDataOnce() {
            User user = withdrawnUser(randomNickname());
            UUID userId = user.getId();

            service.anonymize(userId);
            entityManager.flush();
            entityManager.clear();

            assertThat(row("""
                    SELECT name, email, nickname, TO_CHAR(birth_date, 'YYYY-MM-DD'), profile_image_url, gender, is_deleted
                    FROM users WHERE id = ?""", userId))
                    .containsExactly("", "", "탈퇴한 사용자", "1900-01-01", Profile.DEFAULT_PROFILE_IMAGE_URL, "FEMALE",
                            true);
            assertThat(column("SELECT nickname FROM user_nickname_histories WHERE user_id = ?", userId))
                    .hasSize(2).containsOnly("");
            assertThat(column("SELECT provider_user_id FROM user_social_accounts WHERE user_id = ?", userId))
                    .hasSize(1).containsOnly("");
            assertThat(column("SELECT anonymized FROM user_withdrawals WHERE user_id = ?", userId))
                    .containsExactly(true);

            Object userUpdatedAt = column("SELECT updated_at FROM users WHERE id = ?", userId).get(0);
            Object withdrawalUpdatedAt = column("SELECT updated_at FROM user_withdrawals WHERE user_id = ?", userId)
                    .get(0);
            service.anonymize(userId);
            entityManager.flush();

            assertThat(column("SELECT updated_at FROM users WHERE id = ?", userId)).containsExactly(userUpdatedAt);
            assertThat(column("SELECT updated_at FROM user_withdrawals WHERE user_id = ?", userId))
                    .containsExactly(withdrawalUpdatedAt);
        }

        @Test
        @DisplayName("두 회원을 비식별해 같은 닉네임·빈 소셜 식별자가 되어도 유니크 인덱스와 충돌하지 않는다")
        void anonymizeTwoUsersWithoutIndexConflict() {
            User first = withdrawnUser(randomNickname());
            User second = withdrawnUser(randomNickname());

            service.anonymize(first.getId());
            service.anonymize(second.getId());
            entityManager.flush();

            assertThat(column("SELECT nickname FROM users WHERE id = ?", first.getId()))
                    .containsExactly("탈퇴한 사용자");
            assertThat(column("SELECT nickname FROM users WHERE id = ?", second.getId()))
                    .containsExactly("탈퇴한 사용자");
        }

        @Test
        @DisplayName("스케줄러 주체로 비식별하면 회원·탈퇴 기록의 updated_by가 SYSTEM_SCHEDULER이고 created_by는 그대로다")
        void anonymizeAuditedAsScheduler() {
            String nickname = randomNickname();
            User user = userRepository.save(user(nickname, null));
            entityManager.flush();
            UUID userId = user.getId();
            authenticate(userId);
            service.withdraw(userId, WithdrawReason.USER_REQUESTED);
            entityManager.flush();
            entityManager.clear();
            SecurityContextHolder.clearContext();

            authenticate(Auditor.SYSTEM_SCHEDULER);
            service.anonymize(userId);
            entityManager.flush();

            assertThat(row("SELECT created_by, updated_by FROM users WHERE id = ?", userId))
                    .containsExactly(Auditor.SYSTEM, Auditor.SYSTEM_SCHEDULER);
            assertThat(row("SELECT created_by, updated_by FROM user_withdrawals WHERE user_id = ?", userId))
                    .containsExactly(userId.toString(), Auditor.SYSTEM_SCHEDULER);
        }
    }
}
