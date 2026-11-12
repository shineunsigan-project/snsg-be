package com.shineunsigan.snsg.place.application.dto.command;

import java.math.BigDecimal;
import java.util.UUID;

import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 어드민의 장소 수정(검수). 유형·검수 상태·화면 값 전체를 바꾼다.
 *
 * @param reviewerId 수정한 어드민 id
 */
public record ReviewPlaceCommand(UUID placeId, PlaceType placeType, PlaceStatus status, String name,
        String roadAddress, String addressDetail, BigDecimal latitude, BigDecimal longitude, String regionCode,
        UUID reviewerId) {
}
