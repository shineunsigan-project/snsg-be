package com.shineunsigan.snsg.auth.application.dto.result;

import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;

/**
 * 소셜에서 받아온 사용자 정보.
 * 닉네임·프로필 사진은 제공자가 주지 않을 수 있어 null을 허용한다.
 * 비어 있으면 가입 단계에서 서비스 기본값으로 채운다.
 */
public record SocialUserResult(SocialProvider provider, String providerUserId, String nickname,
        String profileImageUrl) {
}
