package com.shineunsigan.snsg.user.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.model.UserSocialAccount;
import com.shineunsigan.snsg.user.domain.repository.UserSocialAccountRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserSocialAccountRepositoryImpl implements UserSocialAccountRepository {

    private final UserSocialAccountJpaRepository userSocialAccountJpaRepository;

    @Override
    public UserSocialAccount save(UserSocialAccount userSocialAccount) {
        return userSocialAccountJpaRepository.save(userSocialAccount);
    }

    @Override
    public Optional<UserSocialAccount> findByProviderAndProviderUserId(
            SocialProvider provider, String providerUserId) {
        return userSocialAccountJpaRepository
                .findByProviderAndProviderUserIdAndIsDeletedFalse(provider, providerUserId);
    }

    @Override
    public List<UserSocialAccount> findByUserId(UUID userId) {
        return userSocialAccountJpaRepository.findByUserIdAndIsDeletedFalse(userId);
    }
}
