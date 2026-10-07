package com.shineunsigan.snsg.auth.presentation.dto.response;

import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프로필 완성 화면의 기본값. 소셜 식별자는 내보내지 않는다.
 */
public record SignUpSessionResponse(

        @Schema(description = "소셜에서 받아온 닉네임. 화면의 기본값으로 쓰고 사용자가 바꿀 수 있다. 소셜이 주지 않으면 비어 있다",
                example = "민트초코")
        String nickname,

        @Schema(description = "소셜에서 받아온 프로필 사진. 기본 이미지였거나 받지 못하면 비어 있다",
                example = "https://k.kakaocdn.net/dn/abc/profile.jpg")
        String profileImageUrl) {

    public static SignUpSessionResponse from(SocialUserResult socialUser) {
        return new SignUpSessionResponse(socialUser.nickname(), socialUser.profileImageUrl());
    }
}
