package com.shineunsigan.snsg.place.application.port.out;

import com.shineunsigan.snsg.place.application.dto.result.PlaceSourcePageResult;

/** 공공데이터 장소 원천(상가정보). 시군구 하나의 가게를 페이지 단위로 받는다. */
public interface PublicPlaceSourcePort {

    /** 한 페이지에 받는 행 수. 페이지 수는 첫 페이지의 totalCount를 이 값으로 나눠 정한다. */
    int PAGE_SIZE = 1000;

    /**
     * 시군구 하나의 가게 한 페이지를 받는다. 실패해도 예외를 던지지 않고 실패 결과를 준다.
     *
     * @param regionCode 시군구 코드
     * @param pageNo     1부터
     */
    PlaceSourcePageResult fetchPage(String regionCode, int pageNo);
}
