package com.shineunsigan.snsg.place.domain.constants;

/**
 * 검수 결과. 노출은 {@code APPROVED}뿐이다. 폐업은 검수 결과와 따로 {@code isClosed}가 맡는다.
 */
public enum PlaceStatus {
    CANDIDATE,
    APPROVED,
    REJECTED
}
