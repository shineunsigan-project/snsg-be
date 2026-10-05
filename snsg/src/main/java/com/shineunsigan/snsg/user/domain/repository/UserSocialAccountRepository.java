package com.shineunsigan.snsg.user.domain.repository;

import java.util.Optional;

import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.model.UserSocialAccount;

public interface UserSocialAccountRepository {

    UserSocialAccount save(UserSocialAccount userSocialAccount);

    Optional<UserSocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
