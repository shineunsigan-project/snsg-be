package com.shineunsigan.snsg.place.application.dto.result;

import com.shineunsigan.snsg.place.domain.model.SourceStore;

/**
 * 상가정보 응답의 가게 한 행. 원천 값을 거르거나 가공하지 않은 그대로다. 값이 없으면 빈 문자열이다.
 * 좌표는 숫자 표기 문자열로 옮긴다(응답이 숫자든 문자열이든).
 */
public record PlaceSourceRow(String bizesId, String bizesNm, String brchNm, String indsSclsCd, String rdnmAdr,
        String lnoAdr, String flrNo, String lon, String lat) {

    public SourceStore toSourceStore() {
        return new SourceStore(bizesId, bizesNm, brchNm, indsSclsCd, rdnmAdr, lnoAdr, flrNo, lon, lat);
    }
}
