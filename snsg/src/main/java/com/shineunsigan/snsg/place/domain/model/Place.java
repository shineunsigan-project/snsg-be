package com.shineunsigan.snsg.place.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shineunsigan.snsg.common.entity.BaseEntity;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소 마스터. 공공데이터에서 적재한 후보를 사람이 검수해 노출한다.
 * <p>
 * 화면 값(name, roadAddress, addressDetail, latitude, longitude, regionCode)은 사용자에게 보이는 값이고,
 * 원천 값(source*)은 원천 응답에서 마지막으로 받은 값이다. 원천 값은 적재 때마다 갱신하고,
 * 화면 값은 검수 전(CANDIDATE)일 때만 원천 값을 따른다. 검수자가 정한 값을 적재가 덮어쓰지 않기 위해서다.
 * <p>
 * 폐업({@code isClosed})은 검수 결과({@code status})와 따로 둔다. 원천에 다시 나타나면 폐업만 풀리고
 * 검수 결과와 고친 화면 값은 그대로 남는다.
 * <p>
 * 검수는 DB에서 직접 하고(운영 도구 없음), MANUAL 행도 검수자가 DB에 넣는다. 그래서 둘을 위한 메서드가 없다.
 */
@Getter
@Entity
@Table(name = "places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PlaceType placeType;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 200, nullable = false)
    private String roadAddress;

    @Column(length = 100, nullable = false)
    private String addressDetail;

    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal longitude;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 5, nullable = false)
    private String regionCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PlaceStatus status;

    @Column(nullable = false)
    private boolean isClosed;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false, updatable = false)
    private PlaceSource source;

    @Column(length = 50, nullable = false, updatable = false)
    private String sourceId;

    @Column(length = 100, nullable = false)
    private String sourceName;

    @Column(length = 200, nullable = false)
    private String sourceRoadAddress;

    @Column(length = 100, nullable = false)
    private String sourceAddressDetail;

    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal sourceLatitude;

    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal sourceLongitude;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 5, nullable = false)
    private String sourceRegionCode;

    @Column(nullable = false)
    private LocalDateTime lastSyncedAt;

    private Place(PlaceSource source, String sourceId, PlaceType placeType, LocalDateTime syncedAt) {
        this.source = source;
        this.sourceId = sourceId;
        this.placeType = placeType;
        this.status = PlaceStatus.CANDIDATE;
        this.isClosed = false;
        this.lastSyncedAt = syncedAt;
    }

    /**
     * 원천에서 처음 확인한 장소를 검수 대기 후보로 만든다. 화면 값과 원천 값을 같은 원천 값으로 채운다.
     *
     * @param regionCode 그 행을 확인한 지역(조회한 시군구) 코드
     * @param syncedAt   원천에서 확인한 시각
     */
    public static Place createCandidate(PlaceSource source, String sourceId, PlaceType placeType,
            String name, String roadAddress, String addressDetail,
            BigDecimal latitude, BigDecimal longitude, String regionCode, LocalDateTime syncedAt) {
        Place place = new Place(source, sourceId, placeType, syncedAt);
        place.updateSource(name, roadAddress, addressDetail, latitude, longitude, regionCode);
        place.updateDisplay(name, roadAddress, addressDetail, latitude, longitude, regionCode);
        return place;
    }

    /**
     * 원천에서 다시 확인한 값을 반영한다. 원천 값은 항상 바꾸고, 화면 값은 CANDIDATE일 때만 바꾼다.
     * 폐업이었다면 풀린다. 유형과 검수 상태는 바꾸지 않는다.
     *
     * @return APPROVED인데 원천 값이 지난 원천 값과 달라 검수자가 다시 볼 필요가 있으면 true.
     *         화면 값과는 비교하지 않는다. 검수에서 고친 장소가 매번 걸리기 때문이다.
     */
    public boolean confirmSource(String name, String roadAddress, String addressDetail,
            BigDecimal latitude, BigDecimal longitude, String regionCode, LocalDateTime syncedAt) {
        boolean sourceChanged = !isSameSource(name, roadAddress, addressDetail, latitude, longitude, regionCode);
        updateSource(name, roadAddress, addressDetail, latitude, longitude, regionCode);
        if (status == PlaceStatus.CANDIDATE) {
            updateDisplay(name, roadAddress, addressDetail, latitude, longitude, regionCode);
        }
        this.isClosed = false;
        this.lastSyncedAt = syncedAt;
        return status == PlaceStatus.APPROVED && sourceChanged;
    }

    /** 원천에서 사라져 폐업으로 본다. 검수 상태와 값은 그대로 둔다. */
    public void close() {
        this.isClosed = true;
    }

    /** 좌표는 값으로 비교한다. 소수 자릿수 표기만 다른 것은 같은 값이다. */
    private boolean isSameSource(String name, String roadAddress, String addressDetail,
            BigDecimal latitude, BigDecimal longitude, String regionCode) {
        return sourceName.equals(name)
                && sourceRoadAddress.equals(roadAddress)
                && sourceAddressDetail.equals(addressDetail)
                && sourceLatitude.compareTo(latitude) == 0
                && sourceLongitude.compareTo(longitude) == 0
                && sourceRegionCode.equals(regionCode);
    }

    private void updateSource(String name, String roadAddress, String addressDetail,
            BigDecimal latitude, BigDecimal longitude, String regionCode) {
        this.sourceName = name;
        this.sourceRoadAddress = roadAddress;
        this.sourceAddressDetail = addressDetail;
        this.sourceLatitude = latitude;
        this.sourceLongitude = longitude;
        this.sourceRegionCode = regionCode;
    }

    private void updateDisplay(String name, String roadAddress, String addressDetail,
            BigDecimal latitude, BigDecimal longitude, String regionCode) {
        this.name = name;
        this.roadAddress = roadAddress;
        this.addressDetail = addressDetail;
        this.latitude = latitude;
        this.longitude = longitude;
        this.regionCode = regionCode;
    }
}
