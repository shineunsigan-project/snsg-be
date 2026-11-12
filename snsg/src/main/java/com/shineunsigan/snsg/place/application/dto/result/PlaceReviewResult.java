package com.shineunsigan.snsg.place.application.dto.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.model.Place;

/**
 * 어드민이 보는 장소. 화면 값·원천 값·감사 값을 모두 담는다.
 *
 * @param sourceLatitude   MANUAL이면 0
 * @param sourceLongitude  MANUAL이면 0
 * @param sourceRegionCode MANUAL이면 ''. 컬럼이 CHAR(5)라 ''가 공백 다섯 칸으로 읽혀 앞뒤 공백을 뺀다
 * @param reviewedBy       마지막으로 수정·등록한 어드민 id. API로 검수한 적 없으면 ''
 * @param reviewedAt       마지막으로 수정·등록한 시각. API로 검수한 적 없으면 null
 */
public record PlaceReviewResult(UUID id, PlaceType placeType, PlaceStatus status, String name, String roadAddress,
        String addressDetail, BigDecimal latitude, BigDecimal longitude, String regionCode, boolean closed,
        PlaceSource source, String sourceId, String sourceName, String sourceRoadAddress, String sourceAddressDetail,
        BigDecimal sourceLatitude, BigDecimal sourceLongitude, String sourceRegionCode, LocalDateTime lastSyncedAt,
        LocalDateTime createdAt, String createdBy, LocalDateTime updatedAt, String updatedBy, String reviewedBy,
        LocalDateTime reviewedAt) {

    public static PlaceReviewResult from(Place place) {
        return new PlaceReviewResult(place.getId(), place.getPlaceType(), place.getStatus(), place.getName(),
                place.getRoadAddress(), place.getAddressDetail(), place.getLatitude(), place.getLongitude(),
                place.getRegionCode(), place.isClosed(), place.getSource(), place.getSourceId(),
                place.getSourceName(), place.getSourceRoadAddress(), place.getSourceAddressDetail(),
                plainZero(place.getSourceLatitude()), plainZero(place.getSourceLongitude()),
                place.getSourceRegionCode().strip(), place.getLastSyncedAt(), place.getCreatedAt(),
                place.getCreatedBy(), place.getUpdatedAt(), place.getUpdatedBy(), place.getReviewedBy(),
                place.getReviewedAt());
    }

    /** MANUAL의 원천 좌표 0은 DB에서 소수 7자리로 읽혀(0E-7) JSON에 지수 표기로 나간다. 0으로 바꾼다. */
    private static BigDecimal plainZero(BigDecimal value) {
        return value.signum() == 0 ? BigDecimal.ZERO : value;
    }
}
