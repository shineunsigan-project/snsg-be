package com.shineunsigan.snsg.place.presentation.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import com.shineunsigan.snsg.place.application.dto.command.ReviewPlaceCommand;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 장소 수정(검수). 필드를 모두 보낸다(부분 수정 없음). 형식은 여기서 먼저 걸러 틀린 필드를 알려주고,
 * 같은 규칙을 {@code Place}가 불변식으로 한 번 더 지킨다. 이름·주소 길이는 앞뒤 공백을 뺀 길이로 본다.
 * 검수 상태가 APPROVED·REJECTED인지, 유형 미정 승인, 지역이 있는지는 도메인·서비스에서만 판정한다.
 */
public record AdminPlaceUpdateRequest(

        @Schema(description = "장소 유형. BOARD_GAME_CAFE(보드게임 카페), ESCAPE_ROOM(방탈출), PARK(공원), "
                + "UNCLASSIFIED(유형 미정. 승인할 수 없고 거절만 된다)", example = "ESCAPE_ROOM")
        @NotNull(message = PLACE_TYPE_MESSAGE)
        PlaceType placeType,

        @Schema(description = "검수 상태. APPROVED(승인, 노출) 또는 REJECTED(거절, 노출하지 않음)만 보낼 수 있다. "
                + "CANDIDATE를 보내면 400 INVALID_REVIEW_STATUS", example = "APPROVED")
        @NotNull(message = STATUS_MESSAGE)
        PlaceStatus status,

        @Schema(description = "화면에 보일 장소명. 앞뒤 공백을 뺀 길이로 1~100자. 저장되는 값은 앞뒤 공백을 뺀 값이다",
                example = "키이스케이프 홍대점")
        @NotNull(message = NAME_MESSAGE)
        @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE)
        String name,

        @Schema(description = "도로명 주소. 앞뒤 공백을 뺀 길이로 1~200자. 저장되는 값은 앞뒤 공백을 뺀 값이다",
                example = "서울특별시 마포구 와우산로 1")
        @NotNull(message = ROAD_ADDRESS_MESSAGE)
        @Pattern(regexp = ROAD_ADDRESS_PATTERN, message = ROAD_ADDRESS_MESSAGE)
        String roadAddress,

        @Schema(description = "상세주소(층·호 등). 앞뒤 공백을 뺀 길이로 0~100자. 없으면 빈 문자열을 보낸다(생략·null 불가)",
                example = "3층")
        @NotNull(message = ADDRESS_DETAIL_MESSAGE)
        @Pattern(regexp = ADDRESS_DETAIL_PATTERN, message = ADDRESS_DETAIL_MESSAGE)
        String addressDetail,

        @Schema(description = "위도. 33~39(경계 포함). 소수 8자리부터는 7자리로 반올림(HALF_UP)해 저장한다",
                example = "37.5512345")
        @NotNull(message = LATITUDE_MESSAGE)
        @DecimalMin(value = "33", message = LATITUDE_MESSAGE)
        @DecimalMax(value = "39", message = LATITUDE_MESSAGE)
        BigDecimal latitude,

        @Schema(description = "경도. 124~132(경계 포함). 소수 8자리부터는 7자리로 반올림(HALF_UP)해 저장한다",
                example = "126.9212345")
        @NotNull(message = LONGITUDE_MESSAGE)
        @DecimalMin(value = "124", message = LONGITUDE_MESSAGE)
        @DecimalMax(value = "132", message = LONGITUDE_MESSAGE)
        BigDecimal longitude,

        // example은 JSON으로 파싱된다. 따옴표로 감싸야 숫자가 아니라 문자열 예시가 된다
        @Schema(description = "화면 지역 코드. 어드민 지역 목록(GET /api/admin/v1/regions)의 code. 비활성 지역도 된다. "
                + "목록에 없는 코드면 400 INVALID_REGION", example = "\"11440\"")
        @NotBlank(message = REGION_MESSAGE)
        String regionCode) {

    /** 앞뒤 공백을 뺀 값이 1~100자. 줄바꿈도 한 글자로 세려고 {@code .} 대신 {@code [\s\S]}를 쓴다. */
    static final String NAME_PATTERN = "^\\s*\\S(?:[\\s\\S]{0,98}\\S)?\\s*$";

    /** 앞뒤 공백을 뺀 값이 1~200자. */
    static final String ROAD_ADDRESS_PATTERN = "^\\s*\\S(?:[\\s\\S]{0,198}\\S)?\\s*$";

    /** 앞뒤 공백을 뺀 값이 0~100자. 빈 문자열과 공백뿐인 값도 된다. */
    static final String ADDRESS_DETAIL_PATTERN = "^\\s*(?:\\S(?:[\\s\\S]{0,98}\\S)?)?\\s*$";

    static final String PLACE_TYPE_MESSAGE = "장소 유형을 골라주세요.";
    static final String STATUS_MESSAGE = "검수 상태는 승인 또는 거절만 고를 수 있어요.";
    static final String NAME_MESSAGE = "장소명은 1~100자로 적어주세요.";
    static final String ROAD_ADDRESS_MESSAGE = "도로명 주소는 1~200자로 적어주세요.";
    static final String ADDRESS_DETAIL_MESSAGE = "상세주소는 100자 이내로 적어주세요.";
    static final String LATITUDE_MESSAGE = "위도는 33~39 사이로 적어주세요.";
    static final String LONGITUDE_MESSAGE = "경도는 124~132 사이로 적어주세요.";
    static final String REGION_MESSAGE = "선택할 수 없는 지역이에요. 지역을 다시 골라주세요.";

    public ReviewPlaceCommand toCommand(UUID placeId, UUID adminId) {
        return new ReviewPlaceCommand(placeId, placeType, status, name, roadAddress, addressDetail, latitude,
                longitude, regionCode, adminId);
    }
}
