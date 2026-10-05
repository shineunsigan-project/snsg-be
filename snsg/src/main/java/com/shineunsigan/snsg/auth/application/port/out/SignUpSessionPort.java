package com.shineunsigan.snsg.auth.application.port.out;

import java.util.Optional;

import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;

/**
 * 가입 중인 소셜 정보를 잠시 보관한다.
 * <p>
 * 소셜 인증은 끝났지만 성별·생년월일을 아직 받지 못한 상태다. 이 값을 클라이언트가 들고 있다가
 * 가입 요청에 실어 보내면 남의 소셜 계정으로 가입할 수 있으므로 서버가 보관한다.
 * 가입이 끝나면 지운다. 이탈하면 만료로 사라진다.
 */
public interface SignUpSessionPort {

    /**
     * @return 가입을 마칠 때 제시할 토큰
     */
    String save(SocialUserResult socialUser);

    Optional<SocialUserResult> find(String signUpToken);

    void delete(String signUpToken);
}
