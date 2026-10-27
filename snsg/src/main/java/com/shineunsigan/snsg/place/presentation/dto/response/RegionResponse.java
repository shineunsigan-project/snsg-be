package com.shineunsigan.snsg.place.presentation.dto.response;

import com.shineunsigan.snsg.place.application.dto.result.RegionResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegionResponse(

        // example은 JSON으로 파싱된다. 따옴표로 감싸야 숫자가 아니라 문자열 예시가 된다
        @Schema(description = "지역 코드. 행정구역 시군구 코드 5자리 문자열이다", example = "\"11440\"")
        String code,

        @Schema(description = "화면에 보여줄 지역 이름. 시군구 이름이다", example = "마포구")
        String name) {

    public static RegionResponse from(RegionResult result) {
        return new RegionResponse(result.code(), result.sigunguName());
    }
}
