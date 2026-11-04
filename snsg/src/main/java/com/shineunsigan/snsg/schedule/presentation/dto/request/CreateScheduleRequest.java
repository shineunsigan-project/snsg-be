package com.shineunsigan.snsg.schedule.presentation.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.application.dto.command.CreateScheduleCommand;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 형식(필수값, 제목·설명 길이)은 여기서 먼저 걸러 틀린 필드를 알려준다. 같은 규칙을 {@code Schedule}이 불변식으로 한 번 더 지킨다.
 * 제목·설명 길이는 앞뒤 공백을 뺀 길이로 본다. 인원 범위와 시작 일시는 설정값·현재 시각이 필요해 도메인에서만 판정한다.
 */
public record CreateScheduleRequest(

        @Schema(description = "활동 ID. 활동 목록 API(GET /api/v1/activity-categories) 응답의 id",
                example = "01a19cc6-3d80-7000-8000-000000000001")
        @NotNull(message = CATEGORY_MESSAGE)
        UUID categoryId,

        @Schema(description = "장소 ID. 장소 후보 API 응답의 id. 장소는 활동이 고를 수 있는 유형이어야 한다. 장소명·주소·좌표는 보내지 않는다(서버가 장소에서 읽어 저장한다)",
                example = "01a1a2b3-0000-7000-8000-000000000001")
        @NotNull(message = PLACE_MESSAGE)
        UUID placeId,

        @Schema(description = "활동명. 앞뒤 공백을 뺀 길이로 1~20자. 저장되는 값은 앞뒤 공백을 뺀 값이다",
                example = "보드게임 같이 해요")
        @NotNull(message = TITLE_MESSAGE)
        @Pattern(regexp = TITLE_PATTERN, message = TITLE_MESSAGE)
        String title,

        @Schema(description = "예상 소요 시간. SHORT 1시간 내외(60분), NORMAL 2시간 내외(120분), LONG 3시간 이상(180분)",
                example = "NORMAL")
        @NotNull(message = DURATION_TYPE_MESSAGE)
        DurationType durationType,

        @Schema(description = "간략 설명. 앞뒤 공백을 뺀 길이로 10~500자. 줄바꿈을 쓸 수 있다. 저장되는 값은 앞뒤 공백을 뺀 값이다",
                example = "처음 오시는 분도 환영해요. 룰은 같이 배우면서 해요.")
        @NotNull(message = DESCRIPTION_MESSAGE)
        @Pattern(regexp = DESCRIPTION_PATTERN, message = DESCRIPTION_MESSAGE)
        String description,

        @Schema(description = "시작 일시. 오프셋 없는 한국 시간. 분은 00 또는 30이고 초는 0. 지금 + 1시간 이후(같으면 허용)이고 시작 날짜가 오늘 + 30일 이내",
                example = "2026-11-07T19:30:00")
        @NotNull(message = START_AT_MESSAGE)
        LocalDateTime startAt,

        @Schema(description = "인원. 개설자를 포함한 전체 인원으로 4~24", example = "6")
        @NotNull(message = CAPACITY_MESSAGE)
        Integer capacity) {

    /**
     * 앞뒤 공백을 뺀 값이 1~20자. 공백이 아닌 첫 글자와 끝 글자 사이를 센다.
     * 줄바꿈도 한 글자로 세려고 {@code .} 대신 {@code [\s\S]}를 쓴다. 스펙에 실려 FE 정규식으로도 읽힌다.
     */
    static final String TITLE_PATTERN = "^\\s*\\S(?:[\\s\\S]{0,18}\\S)?\\s*$";

    /** 앞뒤 공백을 뺀 값이 10~500자. 줄바꿈도 한 글자로 센다. */
    static final String DESCRIPTION_PATTERN = "^\\s*\\S[\\s\\S]{8,498}\\S\\s*$";

    static final String CATEGORY_MESSAGE = "선택할 수 없는 활동이에요. 활동을 다시 골라주세요.";
    static final String PLACE_MESSAGE = "선택할 수 없는 장소예요. 장소를 다시 골라주세요.";
    static final String TITLE_MESSAGE = "활동명은 1~20자로 적어주세요.";
    static final String DURATION_TYPE_MESSAGE = "예상 소요 시간을 골라주세요.";
    static final String DESCRIPTION_MESSAGE = "10자 이상 적어주세요.";
    static final String START_AT_MESSAGE = "시작 일시는 지금부터 1시간 이후, 30일 이내에서 30분 단위로 골라주세요.";
    static final String CAPACITY_MESSAGE = "인원은 개설자를 포함해 4~24명으로 정해주세요.";

    public CreateScheduleCommand toCommand(UUID userId) {
        return new CreateScheduleCommand(userId, categoryId, placeId, title, durationType, description, startAt,
                capacity);
    }
}
