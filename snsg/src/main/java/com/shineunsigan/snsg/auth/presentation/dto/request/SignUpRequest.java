package com.shineunsigan.snsg.auth.presentation.dto.request;

import java.time.LocalDate;

import com.shineunsigan.snsg.auth.application.dto.command.SignUpCommand;
import com.shineunsigan.snsg.auth.domain.constants.Gender;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

public record SignUpRequest(

        @Schema(description = "일정에서 다른 사람에게 보일 이름. 2~10자의 한글·영문·숫자만 쓸 수 있다. 소셜 닉네임이 기본값이고 바꿀 수 있다",
                example = "민트초코")
        String nickname,

        @Schema(description = "성별. 일정 참여자 구성 표시에 쓰인다", example = "FEMALE")
        @NotNull
        Gender gender,

        @Schema(description = "생년월일. 연령대 계산에 쓰이고 생년월일 자체는 공개되지 않는다", example = "1996-04-12")
        @NotNull
        @Past
        LocalDate birthDate) {

    public SignUpCommand toCommand(String signUpToken) {
        return new SignUpCommand(signUpToken, nickname, gender, birthDate);
    }
}
