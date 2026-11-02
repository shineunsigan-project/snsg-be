package com.shineunsigan.snsg.place.domain.constants;

/**
 * 장소를 가져온 원천. {@code MANUAL}은 원천에 없어 운영자가 직접 넣은 장소라 적재·폐업 처리 대상이 아니다.
 */
public enum PlaceSource {
    SEMAS,
    PARK_STANDARD,
    MANUAL
}
