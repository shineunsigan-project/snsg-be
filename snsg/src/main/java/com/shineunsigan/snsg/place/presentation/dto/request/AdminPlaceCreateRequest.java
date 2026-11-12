package com.shineunsigan.snsg.place.presentation.dto.request;

import static com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceUpdateRequest.ADDRESS_DETAIL_MESSAGE;
import static com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceUpdateRequest.ADDRESS_DETAIL_PATTERN;
import static com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceUpdateRequest.LATITUDE_MESSAGE;
import static com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceUpdateRequest.LONGITUDE_MESSAGE;
import static com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceUpdateRequest.NAME_MESSAGE;
import static com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceUpdateRequest.NAME_PATTERN;
import static com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceUpdateRequest.PLACE_TYPE_MESSAGE;
import static com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceUpdateRequest.REGION_MESSAGE;
import static com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceUpdateRequest.ROAD_ADDRESS_MESSAGE;
import static com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceUpdateRequest.ROAD_ADDRESS_PATTERN;

import java.math.BigDecimal;
import java.util.UUID;

import com.shineunsigan.snsg.place.application.dto.command.CreateManualPlaceCommand;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * MANUAL 장소 등록. 수정 요청에서 검수 상태를 뺀 필드이고 형식 규칙은 같다({@link AdminPlaceUpdateRequest}).
 * 등록하면 바로 승인이라 유형 미정(UNCLASSIFIED)은 400 UNCLASSIFIED_NOT_APPROVABLE이다(도메인에서 판정).
 */
public record AdminPlaceCreateRequest(

        @Schema(description = "장소 유형. BOARD_GAME_CAFE(보드게임 카페), ESCAPE_ROOM(방탈출), PARK(공원). "
                + "등록하면 바로 승인이라 UNCLASSIFIED를 보내면 400 UNCLASSIFIED_NOT_APPROVABLE", example = "BOARD_GAME_CAFE")
        @NotNull(message = PLACE_TYPE_MESSAGE)
        PlaceType placeType,

        @Schema(description = "화면에 보일 장소명. 앞뒤 공백을 뺀 길이로 1~100자. 저장되는 값은 앞뒤 공백을 뺀 값이다",
                example = "레드버튼 신촌점")
        @NotNull(message = NAME_MESSAGE)
        @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE)
        String name,

        @Schema(description = "도로명 주소. 앞뒤 공백을 뺀 길이로 1~200자. 저장되는 값은 앞뒤 공백을 뺀 값이다",
                example = "서울특별시 서대문구 연세로 1")
        @NotNull(message = ROAD_ADDRESS_MESSAGE)
        @Pattern(regexp = ROAD_ADDRESS_PATTERN, message = ROAD_ADDRESS_MESSAGE)
        String roadAddress,

        @Schema(description = "상세주소(층·호 등). 앞뒤 공백을 뺀 길이로 0~100자. 없으면 빈 문자열을 보낸다(생략·null 불가)",
                example = "2층")
        @NotNull(message = ADDRESS_DETAIL_MESSAGE)
        @Pattern(regexp = ADDRESS_DETAIL_PATTERN, message = ADDRESS_DETAIL_MESSAGE)
        String addressDetail,

        @Schema(description = "위도. 33~39(경계 포함). 소수 8자리부터는 7자리로 반올림(HALF_UP)해 저장한다",
                example = "37.5571234")
        @NotNull(message = LATITUDE_MESSAGE)
        @DecimalMin(value = "33", message = LATITUDE_MESSAGE)
        @DecimalMax(value = "39", message = LATITUDE_MESSAGE)
        BigDecimal latitude,

        @Schema(description = "경도. 124~132(경계 포함). 소수 8자리부터는 7자리로 반올림(HALF_UP)해 저장한다",
                example = "126.9361234")
        @NotNull(message = LONGITUDE_MESSAGE)
        @DecimalMin(value = "124", message = LONGITUDE_MESSAGE)
        @DecimalMax(value = "132", message = LONGITUDE_MESSAGE)
        BigDecimal longitude,

        // example은 JSON으로 파싱된다. 따옴표로 감싸야 숫자가 아니라 문자열 예시가 된다
        @Schema(description = "화면 지역 코드. 어드민 지역 목록(GET /api/admin/v1/regions)의 code. 비활성 지역도 된다. "
                + "목록에 없는 코드면 400 INVALID_REGION", example = "\"11410\"")
        @NotBlank(message = REGION_MESSAGE)
        String regionCode) {

    public CreateManualPlaceCommand toCommand(UUID adminId) {
        return new CreateManualPlaceCommand(placeType, name, roadAddress, addressDetail, latitude, longitude,
                regionCode, adminId);
    }
}
