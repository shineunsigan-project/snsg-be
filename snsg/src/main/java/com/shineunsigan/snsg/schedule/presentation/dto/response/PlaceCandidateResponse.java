package com.shineunsigan.snsg.schedule.presentation.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.application.dto.result.ExposedPlaceResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlaceCandidateResponse(

        @Schema(description = "장소 ID. 일정 개설 요청의 placeId로 보낸다",
                example = "01a1a2b3-0000-7000-8000-000000000001")
        UUID id,

        @Schema(description = "장소명", example = "보드게임카페 홍대점")
        String name,

        @Schema(description = "도로명주소. 없으면 지번주소다. 상세주소(층 등)는 들어 있지 않다", example = "서울특별시 마포구 와우산로 1")
        String roadAddress,

        @Schema(description = "상세주소(층 등). 없으면 빈 문자열이다. 표시할 때 도로명주소 뒤에 붙인다", example = "2층")
        String addressDetail,

        @Schema(description = "위도. WGS84 소수", example = "37.5512345")
        BigDecimal latitude,

        @Schema(description = "경도. WGS84 소수", example = "126.9212345")
        BigDecimal longitude,

        @Schema(description = "장소가 속한 지역 코드. 지역 목록 API의 code와 같은 5자리 문자열이다", example = "11440")
        String regionCode) {

    public static PlaceCandidateResponse from(ExposedPlaceResult result) {
        return new PlaceCandidateResponse(result.id(), result.name(), result.roadAddress(), result.addressDetail(),
                result.latitude(), result.longitude(), result.regionCode());
    }
}
