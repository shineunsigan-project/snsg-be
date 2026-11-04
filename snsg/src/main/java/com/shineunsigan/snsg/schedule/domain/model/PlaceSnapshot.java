package com.shineunsigan.snsg.schedule.domain.model;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개설 시점의 장소 사본. 장소가 검수에서 고쳐지거나 폐업해도 이미 열린 일정은 열린 시점 그대로 보인다.
 * place 도메인의 장소를 가리키는 id는 {@link Schedule}이 따로 가진다.
 * 상세주소가 없으면 ''다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceSnapshot {

    @Column(length = 100, nullable = false, updatable = false)
    private String placeName;

    @Column(length = 200, nullable = false, updatable = false)
    private String placeRoadAddress;

    @Column(length = 100, nullable = false, updatable = false)
    private String placeAddressDetail;

    @Column(precision = 10, scale = 7, nullable = false, updatable = false)
    private BigDecimal placeLatitude;

    @Column(precision = 10, scale = 7, nullable = false, updatable = false)
    private BigDecimal placeLongitude;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 5, nullable = false, updatable = false)
    private String regionCode;

    private PlaceSnapshot(String placeName, String placeRoadAddress, String placeAddressDetail,
            BigDecimal placeLatitude, BigDecimal placeLongitude, String regionCode) {
        this.placeName = placeName;
        this.placeRoadAddress = placeRoadAddress;
        this.placeAddressDetail = placeAddressDetail;
        this.placeLatitude = placeLatitude;
        this.placeLongitude = placeLongitude;
        this.regionCode = regionCode;
    }

    public static PlaceSnapshot of(String name, String roadAddress, String addressDetail,
            BigDecimal latitude, BigDecimal longitude, String regionCode) {
        return new PlaceSnapshot(name, roadAddress, addressDetail, latitude, longitude, regionCode);
    }
}
