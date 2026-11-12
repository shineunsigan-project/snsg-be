package com.shineunsigan.snsg.place.domain.model;

import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 어드민 장소 목록의 필터. null인 조건은 걸지 않고, 조건 사이는 AND다.
 *
 * @param regionCode 화면 값의 지역 코드
 * @param closed     폐업 여부
 * @param keyword    이름·도로명 주소·원천 이름·원천 도로명 주소 중 하나에 대소문자 무시 부분 일치.
 *                   {@code %}·{@code _}는 글자 그대로 찾는다
 */
public record PlaceSearchCondition(String regionCode, PlaceStatus status, PlaceType placeType, PlaceSource source,
        Boolean closed, String keyword) {

    /** 지역 코드·키워드가 비었거나 공백뿐이면 그 조건을 걸지 않는다. 키워드는 앞뒤 공백을 뺀다. */
    public static PlaceSearchCondition of(String regionCode, PlaceStatus status, PlaceType placeType,
            PlaceSource source, Boolean closed, String keyword) {
        return new PlaceSearchCondition(blankToNull(regionCode), status, placeType, source, closed,
                blankToNull(keyword));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
