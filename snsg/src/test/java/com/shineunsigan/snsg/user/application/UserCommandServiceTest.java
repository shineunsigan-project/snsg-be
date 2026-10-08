package com.shineunsigan.snsg.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.event.UserWithdrawnEvent;
import com.shineunsigan.snsg.user.application.port.out.FileStoragePort;
import com.shineunsigan.snsg.user.application.port.out.UserEventPort;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.constants.TermsType;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.TermsAgreement;
import com.shineunsigan.snsg.user.domain.model.User;
import com.shineunsigan.snsg.user.domain.model.UserSocialAccount;
import com.shineunsigan.snsg.user.domain.repository.TermsAgreementRepository;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;
import com.shineunsigan.snsg.user.domain.repository.UserSocialAccountRepository;

/**
 * 탈퇴가 애그리거트 전체를 정리하고, 타 도메인이 받을 이벤트를 남기는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    private static final String PROVIDER_USER_ID = "kakao-1";
    private static final String UPLOADED_IMAGE = "/images/profiles/a.png";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;

    @Mock
    private TermsAgreementRepository termsAgreementRepository;

    @Mock
    private TermsProperties termsProperties;

    @Mock
    private FileStoragePort fileStoragePort;

    @Mock
    private UserEventPort userEventPort;

    @InjectMocks
    private UserCommandService userCommandService;

    private User user() {
        return User.create(Profile.of("민트초코", Gender.FEMALE, LocalDate.of(1996, 4, 12), UPLOADED_IMAGE));
    }

    @Test
    @DisplayName("탈퇴하면 소셜 계정과 약관 동의도 함께 삭제된다")
    void withdrawDeletesWholeAggregate() {
        User user = user();
        UUID userId = user.getId();
        UserSocialAccount socialAccount = UserSocialAccount.of(user, SocialProvider.KAKAO, PROVIDER_USER_ID);
        TermsAgreement termsAgreement = TermsAgreement.of(user, TermsType.SERVICE_TERMS, "2026-10-01");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSocialAccountRepository.findByUserId(userId)).thenReturn(List.of(socialAccount));
        when(termsAgreementRepository.findByUserId(userId)).thenReturn(List.of(termsAgreement));

        userCommandService.withdraw(userId, WithdrawReason.USER_REQUESTED);

        assertThat(user.isDeleted()).isTrue();
        assertThat(socialAccount.isDeleted()).isTrue();
        assertThat(termsAgreement.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("탈퇴 이벤트에 비식별 처리 전의 사진 경로를 담는다")
    void withdrawPublishesEventWithOriginalImage() {
        User user = user();
        UUID userId = user.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSocialAccountRepository.findByUserId(userId))
                .thenReturn(List.of(UserSocialAccount.of(user, SocialProvider.KAKAO, PROVIDER_USER_ID)));
        when(termsAgreementRepository.findByUserId(userId)).thenReturn(List.of());

        userCommandService.withdraw(userId, WithdrawReason.USER_REQUESTED);

        ArgumentCaptor<UserWithdrawnEvent> captor = ArgumentCaptor.forClass(UserWithdrawnEvent.class);
        verify(userEventPort).publish(captor.capture());
        UserWithdrawnEvent event = captor.getValue();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(event.providerUserId()).isEqualTo(PROVIDER_USER_ID);
        // 비식별 처리로 기본 이미지가 되기 전의 경로여야 파일을 찾아 지울 수 있다
        assertThat(event.profileImageUrl()).isEqualTo(UPLOADED_IMAGE);
        assertThat(event.reason()).isEqualTo(WithdrawReason.USER_REQUESTED);
    }

    @Test
    @DisplayName("없는 사용자를 탈퇴시키면 USER_NOT_FOUND")
    void withdrawUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userCommandService.withdraw(userId, WithdrawReason.USER_REQUESTED))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("가입하지 않은 소셜 계정의 연결 끊기 통지는 그냥 넘어간다")
    void withdrawBySocialAccountIgnoresUnknownAccount() {
        when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(Optional.empty());

        userCommandService.withdrawBySocialAccount(SocialProvider.KAKAO, PROVIDER_USER_ID);

        verify(userEventPort, never()).publish(any());
    }
}
