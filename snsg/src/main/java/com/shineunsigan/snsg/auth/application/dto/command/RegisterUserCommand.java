package com.shineunsigan.snsg.auth.application.dto.command;

import java.time.LocalDate;

import com.shineunsigan.snsg.auth.domain.constants.Gender;
import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;

/**
 * auth가 user에게 가입을 요청할 때 넘기는 값.
 * 소셜에서 받은 정보({@link SocialUserResult})와 사용자가 입력한 정보를 합친 것이다.
 */
public record RegisterUserCommand(SocialUserResult socialUser, String nickname, Gender gender, LocalDate birthDate) {
}
