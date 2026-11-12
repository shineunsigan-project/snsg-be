package com.shineunsigan.snsg.admin.presentation.dto.request;

import com.shineunsigan.snsg.admin.application.dto.command.AdminLoginCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 형식(정규식)은 보지 않는다. 형식이 틀린 값은 맞는 계정이 없는 것으로 401이 된다.
 * 가입 규칙이 바뀌어도 기존 계정이 로그인할 수 있게 하기 위해서다. 비었는지와 길이 상한만 본다.
 */
public record AdminLoginRequest(

        @Schema(description = "로그인 아이디. 입력한 그대로 보낸다. 최대 64자", example = "mintadmin")
        @NotBlank(message = LOGIN_ID_MESSAGE)
        @Size(max = MAX_LENGTH, message = LOGIN_ID_MESSAGE)
        String loginId,

        @Schema(description = "비밀번호. 최대 64자", example = "mint1234!")
        @NotBlank(message = PASSWORD_MESSAGE)
        @Size(max = MAX_LENGTH, message = PASSWORD_MESSAGE)
        String password) {

    static final int MAX_LENGTH = 64;

    static final String LOGIN_ID_MESSAGE = "아이디를 입력해주세요.";
    static final String PASSWORD_MESSAGE = "비밀번호를 입력해주세요.";

    public AdminLoginCommand toCommand() {
        return new AdminLoginCommand(loginId, password);
    }
}
