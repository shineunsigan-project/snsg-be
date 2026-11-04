package com.shineunsigan.snsg.schedule.presentation.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateScheduleResponse(

        @Schema(description = "만든 일정의 ID",
                example = "01a1a2b3-4c5d-7e6f-8a9b-0c1d2e3f4a5b")
        UUID scheduleId) {
}
