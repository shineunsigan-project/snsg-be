package com.shineunsigan.snsg.place.domain.constants;

/**
 * 장소 유형. 적재 때 추정하고 검수자가 확정한다. {@code UNCLASSIFIED}는 유형을 정하지 못한 후보라 노출할 수 없다.
 */
public enum PlaceType {
    BOARD_GAME_CAFE,
    ESCAPE_ROOM,
    PARK,
    UNCLASSIFIED
}
