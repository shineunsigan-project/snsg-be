package com.shineunsigan.snsg.auth.presentation.dto.request;

import java.time.LocalDate;

import com.shineunsigan.snsg.auth.application.dto.command.SignUpCommand;
import com.shineunsigan.snsg.auth.domain.constants.Gender;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 형식은 여기서 먼저 걸러 틀린 필드를 알려준다. 같은 규칙을 user 도메인이 불변식으로 한 번 더 지킨다.
 * auth는 user 도메인을 import할 수 없어 정규식과 문구가 두 곳에 적힌다.
 * 비었을 때도 형식 오류 문구를 쓴다. 화면은 필드마다 문구 하나로 안내하면 된다.
 */
public record SignUpRequest(

        @Schema(description = "실명. 2~20자의 한글 또는 영문만 쓸 수 있다. 공백·숫자는 받지 않는다. 남에게 보이지 않고 가입 후 바꿀 수 없다",
                example = "김민트")
        @NotNull(message = NAME_MESSAGE)
        @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE)
        String name,

        @Schema(description = "일정에서 다른 사람에게 보일 이름. 2~10자의 한글·영문·숫자·.·_만 쓸 수 있다. 대소문자만 다른 닉네임은 같은 닉네임으로 본다. 소셜 닉네임이 기본값이고 바꿀 수 있다",
                example = "민트초코")
        @NotNull(message = NICKNAME_MESSAGE)
        @Pattern(regexp = NICKNAME_PATTERN, message = NICKNAME_MESSAGE)
        String nickname,

        @Schema(description = "이메일. 선택 입력이다. 생략하거나 빈 문자열이면 없음으로 저장한다. local@domain.tld 형식, 최대 100자. 앞뒤 공백은 다듬지 않고 거부한다. 대소문자는 입력한 그대로 저장한다",
                example = "mint@example.com")
        @Size(max = EMAIL_MAX_LENGTH, message = EMAIL_MESSAGE)
        @Pattern(regexp = EMAIL_PATTERN, message = EMAIL_MESSAGE)
        String email,

        @Schema(description = "성별. 일정 참여자 구성 표시에 쓰인다", example = "FEMALE")
        @NotNull(message = GENDER_MESSAGE)
        Gender gender,

        @Schema(description = "생년월일. 연령대 계산에 쓰이고 생년월일 자체는 공개되지 않는다. 가입일 기준 만 18세 이상이어야 한다",
                example = "1996-04-12")
        @NotNull(message = BIRTH_DATE_MESSAGE)
        @Past(message = BIRTH_DATE_MESSAGE)
        LocalDate birthDate,

        @Schema(description = "프로필 사진 업로드 API가 돌려준 경로. 비워 두면 소셜에서 받아온 사진을 쓰고, 그것도 없으면 기본 이미지가 된다",
                example = "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png")
        String profileImageUrl) {

    static final String NAME_PATTERN = "^[가-힣a-zA-Z]{2,20}$";
    static final String NICKNAME_PATTERN = "^[가-힣a-zA-Z0-9._]{2,10}$";
    static final String EMAIL_PATTERN = "^([A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,})?$";
    static final int EMAIL_MAX_LENGTH = 100;

    static final String NAME_MESSAGE = "이름은 2~20자의 한글 또는 영문만 쓸 수 있어요.";
    static final String NICKNAME_MESSAGE = "닉네임은 2~10자의 한글·영문·숫자·.·_만 쓸 수 있어요.";
    static final String EMAIL_MESSAGE = "이메일 형식이 올바르지 않아요.";
    static final String GENDER_MESSAGE = "성별을 선택해주세요.";
    static final String BIRTH_DATE_MESSAGE = "생년월일이 올바르지 않습니다.";

    public SignUpCommand toCommand(String signUpToken) {
        return new SignUpCommand(signUpToken, name, email, nickname, gender, birthDate, profileImageUrl);
    }
}
