package com.shineunsigan.snsg.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

/**
 * 이 경로는 인증 없이 열려 있다. 발신자 확인이 뚫리면 누구나 남의 계정을 탈퇴시킬 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class HandleSocialUnlinkUseCaseTest {

    private static final String ADMIN_KEY = "real-admin-key";
    private static final String PROVIDER_USER_ID = "kakao-1";

    @Mock
    private UserPort userPort;

    private HandleSocialUnlinkUseCase useCase() {
        return new HandleSocialUnlinkUseCase(userPort, new KakaoWebhookProperties(ADMIN_KEY));
    }

    @Test
    @DisplayName("어드민 키가 맞으면 탈퇴시킨다")
    void withdrawsWithValidAdminKey() {
        useCase().handleKakaoUnlink("KakaoAK " + ADMIN_KEY, PROVIDER_USER_ID);

        verify(userPort).withdrawBySocialAccount(SocialProvider.KAKAO, PROVIDER_USER_ID);
    }

    @Test
    @DisplayName("어드민 키가 다르면 탈퇴시키지 않는다")
    void rejectsWrongAdminKey() {
        assertThatThrownBy(() -> useCase().handleKakaoUnlink("KakaoAK wrong-key", PROVIDER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_WEBHOOK_SENDER);

        verify(userPort, never()).withdrawBySocialAccount(any(), anyString());
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 탈퇴시키지 않는다")
    void rejectsMissingHeader() {
        assertThatThrownBy(() -> useCase().handleKakaoUnlink(null, PROVIDER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_WEBHOOK_SENDER);

        verify(userPort, never()).withdrawBySocialAccount(any(), anyString());
    }

    @Test
    @DisplayName("키만 보내고 KakaoAK 형식이 아니면 탈퇴시키지 않는다")
    void rejectsRawKeyWithoutScheme() {
        assertThatThrownBy(() -> useCase().handleKakaoUnlink(ADMIN_KEY, PROVIDER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_WEBHOOK_SENDER);
    }
}
