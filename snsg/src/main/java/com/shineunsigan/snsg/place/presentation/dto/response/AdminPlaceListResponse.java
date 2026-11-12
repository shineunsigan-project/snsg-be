package com.shineunsigan.snsg.place.presentation.dto.response;

import java.util.List;

import com.shineunsigan.snsg.place.application.dto.result.PlaceReviewPageResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminPlaceListResponse(

        @Schema(description = "장소. 만든 일시(createdAt)가 빠른 순(같으면 id 순)이다. 없으면 빈 배열")
        List<AdminPlaceResponse> items,

        @Schema(description = """
                다음 페이지 커서. 다음 페이지를 부를 때 같은 필터에 그대로 cursor로 보낸다. 마지막 페이지면 null이다.
                내용을 해석하지 않는 불투명 문자열이다(영문·숫자·_·-만 쓴다)""",
                example = "MjAyNi0xMS0wMlQxMDoxNTozMHwwMWExYTJiMy0wMDAwLTcwMDAtODAwMC0wMDAwMDAwMDAwMDE",
                nullable = true)
        String nextCursor) {

    public static AdminPlaceListResponse from(PlaceReviewPageResult result) {
        return new AdminPlaceListResponse(result.items().stream().map(AdminPlaceResponse::from).toList(),
                result.nextCursor());
    }
}
