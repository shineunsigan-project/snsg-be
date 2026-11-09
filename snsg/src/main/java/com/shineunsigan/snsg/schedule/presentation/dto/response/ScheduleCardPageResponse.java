package com.shineunsigan.snsg.schedule.presentation.dto.response;

import java.util.List;

import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleListResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record ScheduleCardPageResponse(

        @Schema(description = "일정 카드. 시작 일시가 빠른 순이다. 없으면 빈 배열")
        List<ScheduleCardResponse> items,

        @Schema(description = """
                다음 페이지 커서. 다음 페이지를 부를 때 그대로 cursor로 보낸다. 마지막 페이지면 null이다.
                내용을 해석하지 않는 불투명 문자열이다(영문·숫자·_·-만 쓴다)""",
                example = "MjAyNi0xMS0xNFQxOTozMHwwMWExYzNkNC0wMDAwLTcwMDAtODAwMC0wMDAwMDAwMDAwMDE",
                nullable = true)
        String nextCursor) {

    public static ScheduleCardPageResponse from(ScheduleListResult result) {
        return new ScheduleCardPageResponse(result.items().stream().map(ScheduleCardResponse::from).toList(),
                result.nextCursor());
    }
}
