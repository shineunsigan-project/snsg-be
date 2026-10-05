package com.shineunsigan.snsg.user.application.port.in;

import java.util.UUID;

import com.shineunsigan.snsg.user.application.dto.command.RegisterUserCommand;

/**
 * 타 도메인이 사용자를 가입시키는 입구. 사용자·소셜 계정·약관 동의를 한 트랜잭션에서 만든다.
 */
public interface RegisterUserUseCase {

    UUID register(RegisterUserCommand command);
}
