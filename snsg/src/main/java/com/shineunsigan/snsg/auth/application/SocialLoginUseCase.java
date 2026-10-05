package com.shineunsigan.snsg.auth.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shineunsigan.snsg.auth.application.dto.result.SocialAuthResult;
import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.application.port.out.SignUpSessionPort;
import com.shineunsigan.snsg.auth.application.port.out.SocialLoginPort;
import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 소셜 인증을 처리한다. 기존 회원이면 로그인시키고, 신규면 가입 토큰만 발급한다.
 * <p>
 * 여기서 사용자를 만들지 않는다. 성별·생년월일을 아직 받지 못했기 때문이다.
 * 트랜잭션을 걸지 않는다. 외부 호출(소셜 API)이 들어 있어 DB 커넥션을 붙잡으면 안 된다.
 */
@Service
@RequiredArgsConstructor
public class SocialLoginUseCase {

    private final List<SocialLoginPort> socialLoginPorts;
    private final UserPort userPort;
    private final SignUpSessionPort signUpSessionPort;
    private final TokenIssuer tokenIssuer;

    public String authorizeUrl(SocialProvider provider) {
        return socialLoginPort(provider).authorizeUrl();
    }

    public SocialAuthResult authenticate(SocialProvider provider, String authorizationCode) {
        SocialUserResult socialUser = socialLoginPort(provider).fetchUser(authorizationCode);

        Optional<UUID> userId = userPort.findIdBySocialAccount(socialUser.provider(), socialUser.providerUserId());
        if (userId.isPresent()) {
            return new SocialAuthResult.LoggedIn(tokenIssuer.issue(userId.get()));
        }
        return new SocialAuthResult.SignUpRequired(signUpSessionPort.save(socialUser));
    }

    private SocialLoginPort socialLoginPort(SocialProvider provider) {
        return socialLoginPorts.stream()
                .filter(port -> port.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new BusinessException(AuthErrorCode.UNSUPPORTED_SOCIAL_PROVIDER));
    }
}
