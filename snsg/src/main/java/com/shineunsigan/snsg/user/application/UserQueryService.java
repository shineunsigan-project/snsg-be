package com.shineunsigan.snsg.user.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.user.application.port.in.FindUserUseCase;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.model.UserSocialAccount;
import com.shineunsigan.snsg.user.domain.repository.UserRepository;
import com.shineunsigan.snsg.user.domain.repository.UserSocialAccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService implements FindUserUseCase {

    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;

    @Override
    public Optional<UUID> findIdBySocialAccount(SocialProvider provider, String providerUserId) {
        return userSocialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(UserSocialAccount::getUser)
                .map(user -> user.getId());
    }

    @Override
    public boolean exists(UUID userId) {
        return userRepository.findById(userId).isPresent();
    }
}
