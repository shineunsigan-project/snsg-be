package com.shineunsigan.snsg.user.application.dto.command;

import java.time.LocalDate;

import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;

/**
 * 가입에 필요한 값. 소셜에서 받은 정보와 사용자가 입력한 정보가 함께 들어온다.
 * {@code profileImageUrl}은 소셜이 주지 않을 수 있어 비어 있으면 기본 이미지로 채운다.
 * {@code email}은 선택 입력이라 {@code null}이거나 {@code ''}이면 없음이다.
 */
public record RegisterUserCommand(SocialProvider provider, String providerUserId, String name, String email,
        String nickname, Gender gender, LocalDate birthDate, String profileImageUrl) {
}
