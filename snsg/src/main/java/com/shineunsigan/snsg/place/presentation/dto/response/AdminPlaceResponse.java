package com.shineunsigan.snsg.place.presentation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.shineunsigan.snsg.place.application.dto.result.PlaceReviewResult;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;

import io.swagger.v3.oas.annotations.media.Schema;

/** 어드민 장소 목록 항목과 상세·수정·등록 응답. 모두 같은 필드다. */
public record AdminPlaceResponse(

        @Schema(description = "장소 id. 상세·수정 경로의 placeId", example = "01a1a2b3-0000-7000-8000-000000000001")
        UUID id,

        @Schema(description = "장소 유형. BOARD_GAME_CAFE(보드게임 카페), ESCAPE_ROOM(방탈출), PARK(공원), "
                + "UNCLASSIFIED(유형 미정. 적재가 정하지 못한 후보. 승인할 수 없다)", example = "ESCAPE_ROOM")
        PlaceType placeType,

        @Schema(description = "검수 상태. CANDIDATE(검수 대기. 적재가 만든 후보), APPROVED(승인. 폐업이 아니고 지역이 활성이면 "
                + "사용자에게 노출), REJECTED(거절. 노출하지 않고 적재가 다시 후보로 돌리지 않는다)", example = "APPROVED")
        PlaceStatus status,

        @Schema(description = "화면 값: 사용자에게 보이는 장소명", example = "키이스케이프 홍대점")
        String name,

        @Schema(description = "화면 값: 도로명 주소", example = "서울특별시 마포구 와우산로 1")
        String roadAddress,

        @Schema(description = "화면 값: 상세주소(층·호 등). 없으면 빈 문자열", example = "3층")
        String addressDetail,

        @Schema(description = "화면 값: 위도(소수 7자리까지)", example = "37.5512345")
        BigDecimal latitude,

        @Schema(description = "화면 값: 경도(소수 7자리까지)", example = "126.9212345")
        BigDecimal longitude,

        @Schema(description = "화면 값: 지역 코드(시군구 5자리). 어드민 지역 목록의 code", example = "\"11440\"")
        String regionCode,

        @Schema(description = "폐업 여부. 적재가 원천에서 찾지 못하면 true, 다시 찾으면 false로 바꾼다. 어드민은 바꿀 수 없다. "
                + "true면 승인이어도 노출되지 않는다", example = "false")
        boolean isClosed,

        @Schema(description = "원천. SEMAS(소상공인 상가정보), PARK_STANDARD(공원 표준데이터), MANUAL(어드민이 직접 등록)",
                example = "SEMAS")
        PlaceSource source,

        @Schema(description = "원천의 식별자(상가정보면 상가업소번호). MANUAL이면 빈 문자열", example = "MA010120220800001234")
        String sourceId,

        @Schema(description = "원천 값: 마지막 적재에서 받은 이름. 적재만 바꾼다. MANUAL이면 빈 문자열", example = "키이스케이프 홍대점")
        String sourceName,

        @Schema(description = "원천 값: 마지막 적재에서 받은 도로명 주소. MANUAL이면 빈 문자열", example = "서울특별시 마포구 와우산로 1")
        String sourceRoadAddress,

        @Schema(description = "원천 값: 마지막 적재에서 받은 상세주소(층). 없거나 MANUAL이면 빈 문자열", example = "3층")
        String sourceAddressDetail,

        @Schema(description = "원천 값: 마지막 적재에서 받은 위도. MANUAL이면 0", example = "37.5512345")
        BigDecimal sourceLatitude,

        @Schema(description = "원천 값: 마지막 적재에서 받은 경도. MANUAL이면 0", example = "126.9212345")
        BigDecimal sourceLongitude,

        @Schema(description = "원천 값: 마지막 적재에서 확인한 지역 코드. MANUAL이면 빈 문자열", example = "\"11440\"")
        String sourceRegionCode,

        @Schema(description = "원천에서 마지막으로 확인한 일시. 한국 시간, 오프셋 없는 ISO-8601. MANUAL이면 등록 일시",
                example = "2026-12-01T03:12:45")
        LocalDateTime lastSyncedAt,

        @Schema(description = "행을 만든 일시(적재 또는 등록). 한국 시간, 오프셋 없는 ISO-8601. 목록 정렬 기준",
                example = "2026-11-02T10:15:30")
        LocalDateTime createdAt,

        @Schema(description = "행을 만든 주체. 어드민 id(MANUAL 등록), SYSTEM_SCHEDULER(적재), SYSTEM(마이그레이션 등 그 밖)",
                example = "SYSTEM_SCHEDULER")
        String createdBy,

        @Schema(description = "마지막으로 바뀐 일시. 한국 시간, 오프셋 없는 ISO-8601. 적재가 확인할 때마다 바뀐다",
                example = "2026-12-01T03:12:45")
        LocalDateTime updatedAt,

        @Schema(description = "마지막으로 바꾼 주체. 어드민 id, SYSTEM_SCHEDULER(적재), SYSTEM(그 밖). "
                + "적재가 확인할 때마다 SYSTEM_SCHEDULER로 덮으므로 검수한 사람은 reviewedBy로 본다", example = "SYSTEM_SCHEDULER")
        String updatedBy,

        @Schema(description = "마지막으로 수정·등록한 어드민 id. 적재는 바꾸지 않는다. API로 검수한 적 없으면 빈 문자열",
                example = "0192f3a4-5b6c-7d8e-9f01-23456789abcd")
        String reviewedBy,

        @Schema(description = "마지막으로 수정·등록한 일시. 한국 시간, 오프셋 없는 ISO-8601. 적재는 바꾸지 않는다. "
                + "API로 검수한 적 없으면 null", example = "2026-11-13T14:20:00", nullable = true)
        LocalDateTime reviewedAt) {

    public static AdminPlaceResponse from(PlaceReviewResult result) {
        return new AdminPlaceResponse(result.id(), result.placeType(), result.status(), result.name(),
                result.roadAddress(), result.addressDetail(), result.latitude(), result.longitude(),
                result.regionCode(), result.closed(), result.source(), result.sourceId(), result.sourceName(),
                result.sourceRoadAddress(), result.sourceAddressDetail(), result.sourceLatitude(),
                result.sourceLongitude(), result.sourceRegionCode(), result.lastSyncedAt(), result.createdAt(),
                result.createdBy(), result.updatedAt(), result.updatedBy(), result.reviewedBy(), result.reviewedAt());
    }
}
