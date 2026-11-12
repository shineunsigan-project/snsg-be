package com.shineunsigan.snsg.place.application.dto.command;

import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 어드민 장소 목록 조회. 필터는 모두 선택이고 null이면 걸지 않는다.
 *
 * @param regionCode 화면 값의 지역 코드. 비었거나 공백뿐이면 걸지 않는다
 * @param keyword    이름·도로명 주소·원천 이름·원천 도로명 주소의 부분 일치. 비었거나 공백뿐이면 걸지 않는다
 * @param cursor     앞 페이지의 nextCursor. 없으면 처음부터
 * @param size       한 페이지 개수. null이면 기본값
 */
public record ListPlacesCommand(String regionCode, PlaceStatus status, PlaceType placeType, PlaceSource source,
        Boolean closed, String keyword, String cursor, Integer size) {
}
