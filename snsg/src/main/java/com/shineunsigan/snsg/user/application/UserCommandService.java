package com.shineunsigan.snsg.user.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.dto.command.RegisterUserCommand;
import com.shineunsigan.snsg.user.application.port.in.RegisterUserUseCase;
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

/**
 * 가입. 탈퇴·비식별은 {@link UserWithdrawalCommandService}, 프로필 수정은 {@link UserProfileCommandService}가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class UserCommandService implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final TermsProperties termsProperties;

    /**
     * 사용자·소셜 계정·약관 동의를 한 트랜잭션에서 만든다.
     * 로그인하면 약관에 동의한 것으로 간주하므로 동의 이력을 함께 남긴다.
     * 중복 판정을 통과한 뒤 동시 가입으로 닉네임 인덱스를 위반해도 저장이 DUPLICATED_NICKNAME을 던진다.
     */
    @Override
    @Transactional
    public UUID register(RegisterUserCommand command) {
        validateNicknameNotTaken(command.nickname());
        validateProfileImageNotUsed(command.profileImageUrl());
        User user = saveUser(command);
        saveSocialAccount(user, command);
        agreeToTerms(user);
        return user.getId();
    }

    private void validateNicknameNotTaken(String nickname) {
        if (userRepository.isNicknameTaken(nickname, LocalDateTime.now())) {
            throw new BusinessException(UserErrorCode.DUPLICATED_NICKNAME);
        }
    }

    /**
     * 다른 회원(보관 중인 탈퇴 회원 포함)이 쓰는 우리 업로드 경로는 받지 않는다. 소셜 사진 주소와 빈 값은 보지 않는다.
     * 접두사만 맞고 형식이 틀린 경로는 {@link Profile#of}가 거부한다.
     */
    private void validateProfileImageNotUsed(String profileImageUrl) {
        if (ProfileImage.isUploadedPath(profileImageUrl)
                && userRepository.isProfileImageUsedByOthers(profileImageUrl, null)) {
            throw new BusinessException(UserErrorCode.INVALID_PROFILE_IMAGE_URL);
        }
    }

    private User saveUser(RegisterUserCommand command) {
        return userRepository.save(User.create(command.name(), command.email(),
                Profile.of(command.nickname(), command.gender(), command.birthDate(), command.profileImageUrl()),
                LocalDate.now()));
    }

    private void saveSocialAccount(User user, RegisterUserCommand command) {
        userSocialAccountRepository.save(
                UserSocialAccount.of(user, command.provider(), command.providerUserId()));
    }

    private void agreeToTerms(User user) {
        termsAgreementRepository.saveAll(List.of(
                TermsAgreement.of(user, TermsType.PRIVACY_POLICY, termsProperties.privacyPolicyVersion()),
                TermsAgreement.of(user, TermsType.SERVICE_TERMS, termsProperties.serviceTermsVersion())));
    }
}
