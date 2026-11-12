package com.shineunsigan.snsg.place.application.dto.command;

import java.math.BigDecimal;
import java.util.UUID;

import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 어드민의 MANUAL 장소 등록. 등록하면 바로 승인이다.
 *
 * @param reviewerId 등록한 어드민 id
 */
public record CreateManualPlaceCommand(PlaceType placeType, String name, String roadAddress, String addressDetail,
        BigDecimal latitude, BigDecimal longitude, String regionCode, UUID reviewerId) {
}
