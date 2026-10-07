package com.shineunsigan.snsg.user.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.dto.command.RegisterUserCommand;
import com.shineunsigan.snsg.user.application.port.in.RegisterUserUseCase;
import com.shineunsigan.snsg.user.application.port.in.UploadProfileImageUseCase;
import com.shineunsigan.snsg.user.application.port.out.FileStoragePort;
import com.shineunsigan.snsg.user.domain.constants.TermsType;
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
public class UserCommandService implements RegisterUserUseCase, UploadProfileImageUseCase {

    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final TermsProperties termsProperties;
    private final FileStoragePort fileStoragePort;

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
}
