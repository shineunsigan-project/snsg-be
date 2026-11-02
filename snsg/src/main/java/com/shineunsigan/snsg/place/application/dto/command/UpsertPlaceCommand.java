package com.shineunsigan.snsg.place.application.dto.command;

import java.math.BigDecimal;

import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 원천에서 확인한 장소 하나. 값 정리와 유형 추정은 이미 끝난 상태로 받는다.
 *
 * @param addressDetail 정리된 상세주소(층 등). 없으면 ''
 * @param regionCode    그 행을 확인한 지역(조회한 시군구) 코드
 * @param placeType     추정 유형. 새 후보를 만들 때만 쓴다
 */
public record UpsertPlaceCommand(PlaceSource source, String sourceId, String name, String roadAddress,
        String addressDetail, BigDecimal latitude, BigDecimal longitude, String regionCode, PlaceType placeType) {
}
