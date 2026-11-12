package com.shineunsigan.snsg.place.domain.model;

/**
 * 상가정보 원천의 가게 한 행. 응답 값을 정리하지 않은 그대로 담는다. 값이 없으면 null이나 빈 문자열일 수 있다.
 *
 * @param sourceId     상가업소번호(bizesId)
 * @param storeName    상호명(bizesNm)
 * @param branchName   지점명(brchNm)
 * @param categoryCode 상권업종 소분류 코드(indsSclsCd)
 * @param roadAddress  도로명 주소(rdnmAdr)
 * @param lotAddress   지번 주소(lnoAdr)
 * @param floor        층(flrNo)
 * @param longitude    경도(lon). 숫자 표기 문자열
 * @param latitude     위도(lat). 숫자 표기 문자열
 */
public record SourceStore(String sourceId, String storeName, String branchName, String categoryCode,
        String roadAddress, String lotAddress, String floor, String longitude, String latitude) {

    public boolean hasSourceId() {
        return sourceId != null && !sourceId.isBlank();
    }
}
