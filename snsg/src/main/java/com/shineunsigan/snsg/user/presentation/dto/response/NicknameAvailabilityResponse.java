package com.shineunsigan.snsg.user.presentation.dto.response;

import com.shineunsigan.snsg.user.application.dto.result.NicknameAvailabilityResult;
import com.shineunsigan.snsg.user.domain.constants.NicknameUnavailableReason;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameAvailabilityResponse(

        @Schema(description = "쓸 수 있는 닉네임이면 true", example = "false")
        boolean available,

        @Schema(description = """
                쓸 수 없는 이유. available이 true면 null이다.
                INVALID_FORMAT: 2~10자의 한글·영문·숫자·.·_가 아니다. 형식 안내 문구를 보여준다.
                DUPLICATED: 다른 회원이 쓰고 있거나 보호 기간(변경 전 14일, 탈퇴 90일)이 남은 닉네임이다. 둘을 구분하지 않는다. 다른 닉네임을 입력하게 한다""",
                example = "DUPLICATED", nullable = true)
        NicknameUnavailableReason reason) {

    public static NicknameAvailabilityResponse from(NicknameAvailabilityResult result) {
        return new NicknameAvailabilityResponse(result.available(), result.reason());
    }
}
