package com.shineunsigan.snsg.auth.application.dto.command;

import java.time.LocalDate;

import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.domain.constants.Gender;

/**
 * 가입 완료 요청. 소셜 정보는 담지 않는다. 서버가 가입 세션에서 꺼낸다.
 */
public record SignUpCommand(String signUpToken, String nickname, Gender gender, LocalDate birthDate) {

    public RegisterUserCommand toRegisterUserCommand(SocialUserResult socialUser) {
        return new RegisterUserCommand(socialUser, nickname, gender, birthDate);
    }
}
