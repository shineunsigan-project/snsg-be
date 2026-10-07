package com.shineunsigan.snsg.auth.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.auth.application.dto.command.SignUpCommand;
import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.application.port.out.SignUpSessionPort;
import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 프로필을 받아 가입을 마친다. 소셜 정보는 클라이언트가 아니라 가입 세션에서 꺼낸다.
 * 클라이언트가 보낸 값을 믿으면 남의 소셜 계정으로 가입할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class CompleteSignUpUseCase {

    /** 업로드 API가 돌려주는 경로. 클라이언트가 임의의 외부 주소를 넣지 못하게 막는다. */
    private static final String UPLOADED_IMAGE_PREFIX = "/images/profiles/";

    private final SignUpSessionPort signUpSessionPort;
    private final UserPort userPort;
    private final TokenIssuer tokenIssuer;

    public TokenResult completeSignUp(SignUpCommand command) {
        validateProfileImageUrl(command.profileImageUrl());

        SocialUserResult socialUser = signUpSessionPort.find(command.signUpToken())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_SIGNUP_TOKEN));

        UUID userId = userPort.register(command.toRegisterUserCommand(socialUser));

        // 1회용이다. 같은 토큰으로 두 번 가입하면 중복 계정 시도가 된다.
        signUpSessionPort.delete(command.signUpToken());

        return tokenIssuer.issue(userId);
    }

    public String uploadProfileImage(String signUpToken, byte[] content) {
        signUpSessionPort.find(signUpToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_SIGNUP_TOKEN));
        return userPort.uploadProfileImage(content);
    }

    public SocialUserResult findSession(String signUpToken) {
        return signUpSessionPort.find(signUpToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_SIGNUP_TOKEN));
    }

    /**
     * 우리가 발급한 경로만 받는다. 외부 주소를 그대로 저장하면 그 주소를 쥔 쪽이
     * 프로필을 보는 사람들의 접속 기록을 모을 수 있다.
     */
    private void validateProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return;
        }
        if (!profileImageUrl.startsWith(UPLOADED_IMAGE_PREFIX)) {
            throw new BusinessException(AuthErrorCode.INVALID_PROFILE_IMAGE_URL);
        }
    }
}
