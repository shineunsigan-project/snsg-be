package com.shineunsigan.snsg.schedule.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 사유 형식(필수, 앞뒤 공백을 뺀 길이)은 여기서 먼저 걸러 틀린 필드를 알려준다. 같은 규칙을 {@code Schedule}이 불변식으로 한 번 더 지킨다.
 */
public record CancelScheduleRequest(

        @Schema(description = """
                취소 사유. 필수다. 앞뒤 공백을 뺀 길이로 1~100자. 줄바꿈을 쓸 수 있다. 저장되는 값은 앞뒤 공백을 뺀 값이다.
                취소된 일정 상세의 cancellation.reason으로 참여자에게 보인다""",
                example = "개인 사정으로 일정을 취소하게 됐어요. 죄송해요.")
        @NotNull(message = REASON_MESSAGE)
        @Pattern(regexp = REASON_PATTERN, message = REASON_MESSAGE)
        String reason) {

    /** 앞뒤 공백을 뺀 값이 1~100자. 줄바꿈도 한 글자로 센다. 보조 문자(이모지)는 한 글자다. */
    static final String REASON_PATTERN = "^\\s*\\S(?:[\\s\\S]{0,98}\\S)?\\s*$";

    static final String REASON_MESSAGE = "취소 사유는 1~100자로 적어주세요.";
}
