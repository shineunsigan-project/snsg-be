package com.shineunsigan.snsg.user.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.model.UserSocialAccount;

public interface UserSocialAccountRepository {

    UserSocialAccount save(UserSocialAccount userSocialAccount);

    Optional<UserSocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    List<UserSocialAccount> findByUserId(UUID userId);
}
