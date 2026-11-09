package com.shineunsigan.snsg.user.application.dto.command;

import java.util.UUID;

/**
 * 프로필 수정에서 바꿀 수 있는 값. {@code null}이면 바꾸지 않는다.
 * 이름·성별·생년월일은 바꿀 수 없으므로 담지 않는다.
 * {@code email}이 {@code ''}이면 이메일을 지운다.
 */
public record UpdateMyProfileCommand(UUID userId, String nickname, String email, String profileImageUrl) {
}
