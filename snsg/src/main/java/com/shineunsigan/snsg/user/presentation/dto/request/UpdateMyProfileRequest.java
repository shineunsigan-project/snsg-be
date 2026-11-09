package com.shineunsigan.snsg.user.presentation.dto.request;

import java.util.UUID;

import com.shineunsigan.snsg.user.application.dto.command.UpdateMyProfileCommand;
import com.shineunsigan.snsg.user.domain.model.Profile;
import com.shineunsigan.snsg.user.domain.model.User;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 바꿀 값만 보낸다. 생략한 필드는 바꾸지 않는다.
 * 형식은 여기서 먼저 걸러 틀린 필드를 알려준다. 같은 규칙을 {@code User}가 불변식으로 한 번 더 지킨다.
 * 이름·성별·생년월일은 받지 않는다. 보내도 무시된다.
 */
public record UpdateMyProfileRequest(

        @Schema(description = "새 닉네임. 바꾸지 않으면 생략한다. 2~10자의 한글·영문·숫자·.·_만 쓸 수 있다. 현재와 정확히 같은 값은 바꾸지 않은 것으로 본다. 대소문자만 달라도 변경이다. 마지막 변경 후 14일 안에는 바꿀 수 없다",
                example = "민트라떼", nullable = true)
        @Pattern(regexp = Profile.NICKNAME_PATTERN, message = NICKNAME_MESSAGE)
        String nickname,

        @Schema(description = "새 이메일. 바꾸지 않으면 생략한다. 빈 문자열이면 이메일을 지운다. local@domain.tld 형식, 최대 100자. 앞뒤 공백은 다듬지 않고 거부한다",
                example = "mint@example.com", nullable = true)
        @Size(max = EMAIL_MAX_LENGTH, message = EMAIL_MESSAGE)
        @Pattern(regexp = User.EMAIL_PATTERN, message = EMAIL_MESSAGE)
        String email,

        @Schema(description = "새 프로필 사진 경로. 바꾸지 않으면 생략한다. 프로필 사진 업로드 API(POST /api/v1/users/me/profile-image)가 돌려준 경로만 받는다. 빈 문자열, 다른 주소, 형식을 바꾼 경로, 다른 회원이 쓰고 있는 경로는 400 INVALID_PROFILE_IMAGE_URL이다. 바꾸면 이전에 올렸던 사진 파일은 지워진다",
                example = "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png", nullable = true)
        String profileImageUrl) {

    static final int EMAIL_MAX_LENGTH = 100;

    static final String NICKNAME_MESSAGE = "닉네임은 2~10자의 한글·영문·숫자·.·_만 쓸 수 있어요.";
    static final String EMAIL_MESSAGE = "이메일 형식이 올바르지 않아요.";

    public UpdateMyProfileCommand toCommand(UUID userId) {
        return new UpdateMyProfileCommand(userId, nickname, email, profileImageUrl);
    }
}
