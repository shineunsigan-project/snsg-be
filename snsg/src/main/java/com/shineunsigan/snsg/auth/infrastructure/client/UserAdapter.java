package com.shineunsigan.snsg.auth.infrastructure.client;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.auth.application.dto.command.RegisterUserCommand;
import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.application.port.out.UserPort;
import com.shineunsigan.snsg.auth.domain.constants.Gender;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.application.port.in.FindUserUseCase;
import com.shineunsigan.snsg.user.application.port.in.RegisterUserUseCase;

import lombok.RequiredArgsConstructor;

/**
 * auth에서 user 도메인을 부르는 유일한 자리. 여기서만 user 패키지를 import한다.
 * 같은 이름의 enum이 양쪽에 있고, 변환은 {@code default} 없는 switch로 한다.
 * 값을 추가하면 컴파일이 깨져 변환 누락을 잡는다.
 */
@Component
@RequiredArgsConstructor
public class UserAdapter implements UserPort {

    private final FindUserUseCase findUserUseCase;
    private final RegisterUserUseCase registerUserUseCase;
    private final com.shineunsigan.snsg.user.application.port.in.UploadProfileImageUseCase uploadProfileImageUseCase;

    @Override
    public Optional<UUID> findIdBySocialAccount(SocialProvider provider, String providerUserId) {
        return findUserUseCase.findIdBySocialAccount(toUserProvider(provider), providerUserId);
    }

    @Override
    public boolean exists(UUID userId) {
        return findUserUseCase.exists(userId);
    }

    @Override
    public UUID register(RegisterUserCommand command) {
        SocialUserResult socialUser = command.socialUser();
        return registerUserUseCase.register(new com.shineunsigan.snsg.user.application.dto.command.RegisterUserCommand(
                toUserProvider(socialUser.provider()),
                socialUser.providerUserId(),
                command.nickname(),
                toUserGender(command.gender()),
                command.birthDate(),
                command.profileImageUrl()));
    }

    @Override
    public String uploadProfileImage(byte[] content) {
        return uploadProfileImageUseCase.upload(content);
    }

    private com.shineunsigan.snsg.user.domain.constants.SocialProvider toUserProvider(SocialProvider provider) {
        return switch (provider) {
            case KAKAO -> com.shineunsigan.snsg.user.domain.constants.SocialProvider.KAKAO;
            case NAVER -> com.shineunsigan.snsg.user.domain.constants.SocialProvider.NAVER;
            case APPLE -> com.shineunsigan.snsg.user.domain.constants.SocialProvider.APPLE;
        };
    }

    private com.shineunsigan.snsg.user.domain.constants.Gender toUserGender(Gender gender) {
        return switch (gender) {
            case FEMALE -> com.shineunsigan.snsg.user.domain.constants.Gender.FEMALE;
            case MALE -> com.shineunsigan.snsg.user.domain.constants.Gender.MALE;
        };
    }
}
