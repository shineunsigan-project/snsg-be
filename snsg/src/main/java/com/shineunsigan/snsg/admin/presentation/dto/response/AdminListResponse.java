package com.shineunsigan.snsg.admin.presentation.dto.response;

import java.util.List;

import com.shineunsigan.snsg.admin.application.dto.result.AdminPageResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminListResponse(

        @Schema(description = "어드민. 가입 신청 일시가 빠른 순(같으면 id 순)이다. 없으면 빈 배열")
        List<AdminSummaryResponse> items,

        @Schema(description = """
                다음 페이지 커서. 다음 페이지를 부를 때 그대로 cursor로 보낸다. 마지막 페이지면 null이다.
                내용을 해석하지 않는 불투명 문자열이다(영문·숫자·_·-만 쓴다)""",
                example = "MjAyNi0xMS0xM1QxMDoxNTozMHwwMTkyZjNhNC01YjZjLTdkOGUtOWYwMS0yMzQ1Njc4OWFiY2Q",
                nullable = true)
        String nextCursor) {

    public static AdminListResponse from(AdminPageResult result) {
        return new AdminListResponse(result.items().stream().map(AdminSummaryResponse::from).toList(),
                result.nextCursor());
    }
}
