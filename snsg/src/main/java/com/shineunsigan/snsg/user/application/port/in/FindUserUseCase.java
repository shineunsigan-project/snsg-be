package com.shineunsigan.snsg.user.application.port.in;

import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.user.domain.constants.SocialProvider;

/**
 * 타 도메인이 사용자를 찾는 입구. 삭제된 사용자는 찾히지 않는다.
 */
public interface FindUserUseCase {

    Optional<UUID> findIdBySocialAccount(SocialProvider provider, String providerUserId);

    boolean exists(UUID userId);
}
