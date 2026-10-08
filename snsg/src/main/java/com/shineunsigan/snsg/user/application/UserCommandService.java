package com.shineunsigan.snsg.user.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.dto.command.RegisterUserCommand;
import com.shineunsigan.snsg.user.application.event.UserWithdrawnEvent;
import com.shineunsigan.snsg.user.application.port.in.RegisterUserUseCase;
import com.shineunsigan.snsg.user.application.port.in.UploadProfileImageUseCase;
import com.shineunsigan.snsg.user.application.port.in.WithdrawUserUseCase;
import com.shineunsigan.snsg.user.application.port.out.FileStoragePort;
import com.shineunsigan.snsg.user.application.port.out.UserEventPort;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.constants.TermsType;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.ProfileImage;
import com.shineunsigan.snsg.user.domain.model.TermsAgreement;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.model.UserSocialAccount;
import com.shineunsigan.snsg.user.domain.repository.TermsAgreementRepository;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;
import com.shineunsigan.snsg.user.domain.repository.UserSocialAccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserCommandService implements RegisterUserUseCase, UploadProfileImageUseCase, WithdrawUserUseCase {

    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final TermsProperties termsProperties;
    private final FileStoragePort fileStoragePort;
    private final UserEventPort userEventPort;

    /**
     * 사진은 바이트를 보고 형식을 판별한다. 클라이언트가 보낸 확장자나 Content-Type은 믿지 않는다.
     * 저장만 하므로 트랜잭션을 걸지 않는다.
     */
    @Override
    public String upload(byte[] content) {
        ProfileImage image = ProfileImage.detect(content);
        return fileStoragePort.store(content, image.extension());
    }

    /**
     * 사용자·소셜 계정·약관 동의를 한 트랜잭션에서 만든다.
     * 로그인하면 약관에 동의한 것으로 간주하므로 동의 이력을 함께 남긴다.
     */
    @Override
    @Transactional
    public UUID register(RegisterUserCommand command) {
        if (userRepository.existsByNickname(command.nickname())) {
            throw new BusinessException(UserErrorCode.DUPLICATED_NICKNAME);
        }

        User user = userRepository.save(User.create(
                Profile.of(command.nickname(), command.gender(), command.birthDate(), command.profileImageUrl())));

        userSocialAccountRepository.save(
                UserSocialAccount.of(user, command.provider(), command.providerUserId()));

        termsAgreementRepository.saveAll(List.of(
                TermsAgreement.of(user, TermsType.PRIVACY_POLICY, termsProperties.privacyPolicyVersion()),
                TermsAgreement.of(user, TermsType.SERVICE_TERMS, termsProperties.serviceTermsVersion())));

        return user.getId();
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
     * 지우는 것은 isDeleted 플래그이고, 프로필은 비식별 값으로 바꾼다.
     * <p>
     * 소셜 연동 해제와 사진 파일 삭제는 커밋 뒤에 이벤트로 처리한다. 외부 호출을 트랜잭션 안에 두지 않는다.
     */
    @Transactional
    public void withdraw(UUID userId, WithdrawReason reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        String profileImageUrl = user.getProfile().getProfileImageUrl();

        user.withdraw();

        List<UserSocialAccount> socialAccounts = userSocialAccountRepository.findByUserId(userId);
        socialAccounts.forEach(UserSocialAccount::delete);
        termsAgreementRepository.findByUserId(userId).forEach(TermsAgreement::delete);

        socialAccounts.forEach(account -> userEventPort.publish(new UserWithdrawnEvent(
                userId, account.getProvider(), account.getProviderUserId(), profileImageUrl, reason)));
    }
}
