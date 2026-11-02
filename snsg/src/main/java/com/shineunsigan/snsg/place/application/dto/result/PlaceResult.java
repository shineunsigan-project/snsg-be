package com.shineunsigan.snsg.place.application.dto.result;

import java.math.BigDecimal;
import java.util.UUID;

import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.model.Place;

/** 노출 장소. 화면 값만 담는다. addressDetail은 층 등 상세주소이고 없으면 ''다. */
public record PlaceResult(UUID id, PlaceType placeType, String name, String roadAddress, String addressDetail,
        BigDecimal latitude, BigDecimal longitude, String regionCode) {

    public static PlaceResult from(Place place) {
        return new PlaceResult(place.getId(), place.getPlaceType(), place.getName(), place.getRoadAddress(),
                place.getAddressDetail(), place.getLatitude(), place.getLongitude(), place.getRegionCode());
    }
}
