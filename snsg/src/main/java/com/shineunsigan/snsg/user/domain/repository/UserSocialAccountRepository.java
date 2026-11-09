package com.shineunsigan.snsg.user.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.model.UserSocialAccount;

/**
 * 조회는 삭제되지 않은 행만 본다. 삭제된 행까지 봐야 하는 메서드는 이름에 드러낸다.
 */
public interface UserSocialAccountRepository {

    UserSocialAccount save(UserSocialAccount userSocialAccount);

    Optional<UserSocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    List<UserSocialAccount> findByUserId(UUID userId);

    /**
     * 사용자의 소셜 계정을 삭제된(탈퇴로 지운) 행까지 모두 읽는다.
     * 탈퇴 회원 비식별에만 쓴다. 로그인·연결 끊기 판정에는 쓰지 않는다. 탈퇴한 계정이 걸린다.
     */
    List<UserSocialAccount> findAllByUserIdIncludingDeleted(UUID userId);
}
