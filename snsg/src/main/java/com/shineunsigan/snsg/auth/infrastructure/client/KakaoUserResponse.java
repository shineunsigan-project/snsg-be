package com.shineunsigan.snsg.auth.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 사용자 정보. 닉네임·프로필 사진은 동의 항목이라 비어 있을 수 있다.
 */
record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

    String nickname() {
        return kakaoAccount == null || kakaoAccount.profile() == null ? null : kakaoAccount.profile().nickname();
    }

    String profileImageUrl() {
        if (kakaoAccount == null || kakaoAccount.profile() == null) {
            return null;
        }
        KakaoProfile profile = kakaoAccount.profile();
        return Boolean.TRUE.equals(profile.isDefaultImage()) ? null : profile.profileImageUrl();
    }

    record KakaoAccount(KakaoProfile profile) {
    }

    record KakaoProfile(String nickname, @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("is_default_image") Boolean isDefaultImage) {
    }
}
