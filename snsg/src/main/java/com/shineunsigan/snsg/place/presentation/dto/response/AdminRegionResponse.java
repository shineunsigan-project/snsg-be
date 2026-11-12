package com.shineunsigan.snsg.place.presentation.dto.response;

import com.shineunsigan.snsg.place.application.dto.result.RegionResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminRegionResponse(

        // example은 JSON으로 파싱된다. 따옴표로 감싸야 숫자가 아니라 문자열 예시가 된다
        @Schema(description = "지역 코드. 행정구역 시군구 코드 5자리 문자열이다. 장소의 regionCode", example = "\"11440\"")
        String code,

        @Schema(description = "시도 이름", example = "서울특별시")
        String sidoName,

        @Schema(description = "시군구 이름", example = "마포구")
        String sigunguName,

        @Schema(description = "서비스 중인 지역인지. false면 그 지역 장소는 승인해도 사용자에게 노출되지 않는다. "
                + "장소 등록·수정에는 쓸 수 있다", example = "true")
        boolean isActive) {

    public static AdminRegionResponse from(RegionResult result) {
        return new AdminRegionResponse(result.code(), result.sidoName(), result.sigunguName(), result.active());
    }
}
