package com.shineunsigan.snsg.schedule.presentation.dto.response;

import java.util.UUID;

import com.shineunsigan.snsg.schedule.application.dto.result.ActivityCategoryResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record ActivityCategoryResponse(

        @Schema(description = "활동 ID. 다른 API에 활동을 넘길 때 이 값을 쓴다",
                example = "01a19cc6-3d80-7000-8000-000000000001")
        UUID id,

        @Schema(description = "활동 코드. 화면에 보여줄 이름(name)이 바뀌어도 그대로인 식별자다", example = "BOARD_GAME")
        String code,

        @Schema(description = "화면에 보여줄 활동 이름", example = "보드게임")
        String name) {

    public static ActivityCategoryResponse from(ActivityCategoryResult result) {
        return new ActivityCategoryResponse(result.id(), result.code(), result.name());
    }
}
