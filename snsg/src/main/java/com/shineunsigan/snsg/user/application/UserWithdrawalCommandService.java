package com.shineunsigan.snsg.user.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.dto.result.AnonymizeTargetResult;
import com.shineunsigan.snsg.user.application.event.UserAnonymizedEvent;
import com.shineunsigan.snsg.user.application.event.UserWithdrawnEvent;
import com.shineunsigan.snsg.user.application.port.in.WithdrawUserUseCase;
import com.shineunsigan.snsg.user.application.port.out.UserEventPort;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.AnonymizeCursor;
import com.shineunsigan.snsg.user.domain.model.ProfileImage;
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

import lombok.RequiredArgsConstructor;

/**
 * 탈퇴와, 보관 기간이 지난 탈퇴 회원의 비식별. User 애그리거트의 구성요소 다섯을 모두 다룬다.
 */
@Service
@RequiredArgsConstructor
public class UserWithdrawalCommandService implements WithdrawUserUseCase {

    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final UserNicknameHistoryRepository userNicknameHistoryRepository;
    private final UserWithdrawalRepository userWithdrawalRepository;
    private final UserEventPort userEventPort;
    private final NicknameProperties nicknameProperties;
    private final WithdrawalProperties withdrawalProperties;

    @Override
    @Transactional
    public void withdraw(UUID userId) {
        withdraw(userId, WithdrawReason.USER_REQUESTED);
    }

    /**
     * 소셜 쪽에서 연동을 끊었다는 통지로 탈퇴시킨다.
     * 가입하지 않은 사용자(동의 화면에서 이탈 등)에 대해서도 통지가 오므로 없으면 넘어간다.
     */
    @Override
    @Transactional
    public void withdrawBySocialAccount(SocialProvider provider, String providerUserId) {
        userSocialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .ifPresent(account -> withdraw(account.getUser().getId(), WithdrawReason.SOCIAL_UNLINKED));
    }

    /**
     * 사용자와 그에 딸린 소셜 계정·약관 동의를 한 트랜잭션에서 지운다. 모두 한 애그리거트라 생명주기가 같다.
     * 지우는 것은 isDeleted 플래그이고 원본 값은 보관 기간 동안 그대로 둔다.
     * 탈퇴 기록과 닉네임 보호 이력을 같은 기준 시각으로 남긴다. 보관 기간과 닉네임 보호 기간은 같은 값이다.
     * <p>
     * 소셜 연동 해제는 커밋 뒤에 이벤트로 처리한다. 외부 호출을 트랜잭션 안에 두지 않는다.
     */
    @Transactional
    public void withdraw(UUID userId, WithdrawReason reason) {
        LocalDateTime now = LocalDateTime.now();
        User user = findUser(userId);
        List<UserSocialAccount> socialAccounts = deleteAggregate(userId, user);
        recordWithdrawal(user, reason, now);
        publishWithdrawnEvents(userId, socialAccounts, reason);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private List<UserSocialAccount> deleteAggregate(UUID userId, User user) {
        user.withdraw();

        List<UserSocialAccount> socialAccounts = userSocialAccountRepository.findByUserId(userId);
        socialAccounts.forEach(UserSocialAccount::delete);
        termsAgreementRepository.findByUserId(userId).forEach(TermsAgreement::delete);
        return socialAccounts;
    }

    private void recordWithdrawal(User user, WithdrawReason reason, LocalDateTime now) {
        userWithdrawalRepository.save(
                UserWithdrawal.of(user, reason, now, nicknameProperties.protectAfterWithdrawal()));
        userNicknameHistoryRepository.save(UserNicknameHistory.withdrawn(
                user, user.getProfile().getNickname(), now, nicknameProperties.protectAfterWithdrawal()));
    }

    private void publishWithdrawnEvents(UUID userId, List<UserSocialAccount> socialAccounts, WithdrawReason reason) {
        socialAccounts.forEach(account -> userEventPort.publish(new UserWithdrawnEvent(
                userId, account.getProvider(), account.getProviderUserId(), reason)));
    }

    /**
     * 비식별 예정 시각이 {@code now} 이전인 대상 한 페이지. {@code after}가 {@code null}이면 첫 페이지다.
     */
    @Transactional(readOnly = true)
    public List<AnonymizeTargetResult> findAnonymizeTargets(LocalDateTime now, AnonymizeTargetResult after) {
        AnonymizeCursor cursor = after == null ? null : new AnonymizeCursor(after.anonymizeDueAt(), after.userId());
        return userWithdrawalRepository
                .findAnonymizeTargets(now, cursor, withdrawalProperties.anonymizePageSize()).stream()
                .map(AnonymizeTargetResult::from)
                .toList();
    }

    /**
     * 탈퇴 회원 한 명을 비식별한다. 회원·닉네임 이력·소셜 계정(삭제된 행 포함)의 개인정보를 지우고 탈퇴 기록에 표시한다.
     * 탈퇴 기록이 없거나(탈퇴하지 않은 회원) 이미 비식별했으면 아무것도 바꾸지 않는다.
     * 비식별 전 사진이 우리 업로드 경로면 커밋 뒤에 파일을 지우도록 이벤트를 낸다.
     */
    @Transactional
    public void anonymize(UUID userId) {
        UserWithdrawal withdrawal = userWithdrawalRepository.findByUserId(userId).orElse(null);
        if (withdrawal == null || withdrawal.isAnonymized()) {
            return;
        }
        User user = withdrawal.getUser();
        String profileImageUrl = user.getProfile().getProfileImageUrl();
        clearPersonalData(userId, user);
        withdrawal.markAnonymized();
        publishAnonymized(userId, profileImageUrl);
    }

    private void clearPersonalData(UUID userId, User user) {
        user.anonymize();
        userNicknameHistoryRepository.findAllByUserIdIncludingDeleted(userId)
                .forEach(UserNicknameHistory::clearNickname);
        userSocialAccountRepository.findAllByUserIdIncludingDeleted(userId)
                .forEach(UserSocialAccount::clearProviderUserId);
    }

    private void publishAnonymized(UUID userId, String profileImageUrl) {
        if (ProfileImage.isUploadedPath(profileImageUrl)) {
            userEventPort.publishUserAnonymized(new UserAnonymizedEvent(userId, profileImageUrl));
        }
    }
}
