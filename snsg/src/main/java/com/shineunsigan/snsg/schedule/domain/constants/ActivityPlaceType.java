package com.shineunsigan.snsg.schedule.domain.constants;

/**
 * 활동이 고를 수 있는 장소 유형. schedule이 소유한다.
 * place 도메인의 장소 유형과 같은 이름의 값을 갖지만 import하지 않는다. 노출할 수 없는 미분류 유형은 두지 않는다.
 */
public enum ActivityPlaceType {
    BOARD_GAME_CAFE,
    ESCAPE_ROOM,
    PARK
}
