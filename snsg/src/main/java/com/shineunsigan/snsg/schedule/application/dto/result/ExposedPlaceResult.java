package com.shineunsigan.snsg.schedule.application.dto.result;

import java.math.BigDecimal;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.constants.ActivityPlaceType;

/** 노출 대상 장소. 일정에 사본으로 남길 화면 값을 담는다. addressDetail은 없으면 ''다. */
public record ExposedPlaceResult(UUID id, ActivityPlaceType placeType, String name, String roadAddress,
        String addressDetail, BigDecimal latitude, BigDecimal longitude, String regionCode) {
}
