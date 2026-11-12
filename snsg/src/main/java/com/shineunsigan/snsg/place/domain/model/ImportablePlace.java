package com.shineunsigan.snsg.place.domain.model;

import java.math.BigDecimal;

import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 적재 대상으로 고르고 값 정리·유형 추정을 마친 원천 행.
 *
 * @param addressDetail 층만 담은 상세주소. 없으면 ''
 * @param latitude      소수 7자리 위도
 * @param longitude     소수 7자리 경도
 */
public record ImportablePlace(String sourceId, String name, String roadAddress, String addressDetail,
        BigDecimal latitude, BigDecimal longitude, PlaceType placeType) {
}
