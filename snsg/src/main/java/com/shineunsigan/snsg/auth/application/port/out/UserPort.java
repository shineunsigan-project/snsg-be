package com.shineunsigan.snsg.auth.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.auth.application.dto.command.RegisterUserCommand;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;

/**
 * user 도메인을 부르는 통로. 구현(어댑터)만 user 패키지를 안다.
 */
public interface UserPort {

    Optional<UUID> findIdBySocialAccount(SocialProvider provider, String providerUserId);

    /**
     * 탈퇴하지 않은 사용자인지 확인한다. 토큰 재발급의 안전망으로 쓴다.
     */
    boolean exists(UUID userId);

    UUID register(RegisterUserCommand command);

    /**
     * 프로필 사진을 올리고 저장된 경로를 받는다.
     */
    String uploadProfileImage(byte[] content);
}
