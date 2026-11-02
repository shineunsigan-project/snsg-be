package com.shineunsigan.snsg.auth.application.dto.command;

import java.time.LocalDate;

import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.domain.constants.Gender;

/**
 * 가입 완료 요청. 소셜 정보는 담지 않는다. 서버가 가입 세션에서 꺼낸다.
 */
public record SignUpCommand(String signUpToken, String name, String email, String nickname, Gender gender,
        LocalDate birthDate, String profileImageUrl) {

    /**
     * 사용자가 사진을 올렸으면 그것을 쓰고, 아니면 소셜에서 받아온 사진을 쓴다.
     */
    public RegisterUserCommand toRegisterUserCommand(SocialUserResult socialUser) {
        String imageUrl = profileImageUrl == null || profileImageUrl.isBlank()
                ? socialUser.profileImageUrl()
                : profileImageUrl;
        return new RegisterUserCommand(socialUser, name, email, nickname, gender, birthDate, imageUrl);
    }
}
