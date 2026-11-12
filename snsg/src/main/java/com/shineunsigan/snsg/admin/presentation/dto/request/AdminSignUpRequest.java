package com.shineunsigan.snsg.admin.presentation.dto.request;

import java.time.LocalDate;

import com.shineunsigan.snsg.admin.application.dto.command.SignUpAdminCommand;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

/**
 * 형식은 여기서 먼저 걸러 틀린 필드를 알려준다. 같은 규칙을 {@code Admin}이 불변식으로 한 번 더 지킨다.
 * 비었을 때도 형식 오류 문구를 쓴다. 역할·상태는 받지 않는다. 본문에 있어도 무시된다.
 */
public record AdminSignUpRequest(

        @Schema(description = "로그인 아이디. 영문 소문자로 시작하는 4~20자의 영문 소문자·숫자. 대문자는 받지 않는다. 다른 어드민과 겹칠 수 없다",
                example = "mintadmin")
        @NotNull(message = LOGIN_ID_MESSAGE)
        @Pattern(regexp = LOGIN_ID_PATTERN, message = LOGIN_ID_MESSAGE)
        String loginId,

        @Schema(description = "비밀번호. 8~64자, 영문과 숫자를 각각 1자 이상. 공백 없는 영문·숫자·특수문자(ASCII)만 쓸 수 있고 한글은 받지 않는다",
                example = "mint1234!")
        @NotNull(message = PASSWORD_MESSAGE)
        @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
        String password,

        @Schema(description = "실명. 2~20자의 한글 또는 영문. 공백·숫자는 받지 않는다", example = "김민트")
        @NotNull(message = NAME_MESSAGE)
        @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE)
        String name,

        @Schema(description = """
                소속팀. PLANNING(기획), DESIGN(디자인), FRONTEND(개발(FE)), BACKEND(개발(BE)), MARKETING(마케팅),
                OPERATIONS(운영), BUSINESS(사업개발), DATA(데이터), MANAGEMENT(경영지원)""",
                example = "BACKEND")
        @NotNull(message = TEAM_MESSAGE)
        AdminTeam team,

        @Schema(description = "생년월일(yyyy-MM-dd). 오늘(한국 시간)보다 이전이어야 한다", example = "1996-04-12")
        @NotNull(message = BIRTH_DATE_MESSAGE)
        @Past(message = BIRTH_DATE_MESSAGE)
        LocalDate birthDate) {

    static final String LOGIN_ID_PATTERN = "^[a-z][a-z0-9]{3,19}$";
    static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,64}$";
    static final String NAME_PATTERN = "^[가-힣a-zA-Z]{2,20}$";

    static final String LOGIN_ID_MESSAGE = "아이디는 영문 소문자로 시작하는 4~20자의 영문 소문자·숫자만 쓸 수 있어요.";
    static final String PASSWORD_MESSAGE = "비밀번호는 영문과 숫자를 각각 1자 이상 넣어 8~64자로 써주세요. 공백·한글은 쓸 수 없어요.";
    static final String NAME_MESSAGE = "이름은 2~20자의 한글 또는 영문만 쓸 수 있어요.";
    static final String TEAM_MESSAGE = "소속팀을 선택해주세요.";
    static final String BIRTH_DATE_MESSAGE = "생년월일이 올바르지 않습니다.";

    public SignUpAdminCommand toCommand() {
        return new SignUpAdminCommand(loginId, password, name, team, birthDate);
    }
}
