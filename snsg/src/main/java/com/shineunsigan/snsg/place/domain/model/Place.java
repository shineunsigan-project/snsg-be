package com.shineunsigan.snsg.place.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shineunsigan.snsg.common.entity.BaseAuditEntity;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.exception.PlaceErrorCode;

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
 * 검수는 어드민이 한다({@link #review}). 원천에 없는 장소는 어드민이 MANUAL로 등록한다({@link #createManual}).
 * 둘 다 원천 값·폐업·마지막 확인 시각은 바꾸지 않는다.
 * <p>
 * {@code updatedBy}는 분기 적재가 확인한 행마다 스케줄러로 덮으므로, 마지막으로 검수한 어드민은
 * {@code reviewedBy}·{@code reviewedAt}에 따로 둔다. 둘은 검수·등록만 바꾼다. API로 검수한 적 없으면 ''와 null이다.
 */
@Getter
@Entity
@Table(name = "places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseAuditEntity {

    private static final int NAME_MAX_LENGTH = 100;
    private static final int ROAD_ADDRESS_MAX_LENGTH = 200;
    private static final int ADDRESS_DETAIL_MAX_LENGTH = 100;

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

    /** 마지막으로 검수(수정·등록)한 어드민 id. 적재는 바꾸지 않는다. API로 검수한 적 없으면 ''다. */
    @Column(length = 50, nullable = false)
    private String reviewedBy = "";

    /** 마지막으로 검수(수정·등록)한 시각. 적재는 바꾸지 않는다. API로 검수한 적 없으면 null이다. */
    private LocalDateTime reviewedAt;

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

    /**
     * 어드민이 원천에 없는 장소를 등록한다. 바로 승인(APPROVED)이고 폐업이 아니다.
     * 원천 식별자와 원천 값은 ''·0이고, 마지막 확인 시각은 등록 시각이다. 적재·폐업 처리 대상이 아니다.
     *
     * @param reviewedBy 등록한 어드민 id
     * @param now        등록 시각
     */
    public static Place createManual(PlaceType placeType, String name, String roadAddress, String addressDetail,
            BigDecimal latitude, BigDecimal longitude, String regionCode, String reviewedBy, LocalDateTime now) {
        Place place = new Place(PlaceSource.MANUAL, "", placeType, now);
        place.updateSource("", "", "", BigDecimal.ZERO, BigDecimal.ZERO, "");
        place.applyReview(placeType, PlaceStatus.APPROVED, name, roadAddress, addressDetail,
                latitude, longitude, regionCode, reviewedBy, now);
        return place;
    }

    /**
     * 어드민의 검수 결과로 유형·검수 상태·화면 값을 모두 바꾼다. 검수 상태는 APPROVED·REJECTED만 받는다.
     * 원천 값·폐업·마지막 확인 시각은 그대로다. 폐업한 장소도 고칠 수 있다(다시 확인되기 전까지 노출되지 않는다).
     *
     * @param reviewedBy 검수한 어드민 id
     * @param reviewedAt 검수 시각
     */
    public void review(PlaceType placeType, PlaceStatus status, String name, String roadAddress, String addressDetail,
            BigDecimal latitude, BigDecimal longitude, String regionCode, String reviewedBy, LocalDateTime reviewedAt) {
        if (status != PlaceStatus.APPROVED && status != PlaceStatus.REJECTED) {
            throw new BusinessException(PlaceErrorCode.INVALID_REVIEW_STATUS);
        }
        applyReview(placeType, status, name, roadAddress, addressDetail, latitude, longitude, regionCode,
                reviewedBy, reviewedAt);
    }

    /**
     * 모두 검증한 뒤에 바꾼다. 하나라도 틀리면 아무것도 바뀌지 않는다.
     * 이름·주소는 앞뒤 공백을 뺀 값을 저장하고, 좌표는 범위(경계 포함)를 본 뒤 소수 7자리로 반올림한다.
     * 지역이 regions에 있는지는 저장된 값이 필요해 서비스가 본다.
     */
    private void applyReview(PlaceType placeType, PlaceStatus status, String name, String roadAddress,
            String addressDetail, BigDecimal latitude, BigDecimal longitude, String regionCode,
            String reviewedBy, LocalDateTime reviewedAt) {
        validateType(placeType, status);
        String validName = validateText(name, 1, NAME_MAX_LENGTH, PlaceErrorCode.INVALID_PLACE_NAME);
        String validRoadAddress = validateText(roadAddress, 1, ROAD_ADDRESS_MAX_LENGTH,
                PlaceErrorCode.INVALID_ROAD_ADDRESS);
        String validAddressDetail = validateText(addressDetail, 0, ADDRESS_DETAIL_MAX_LENGTH,
                PlaceErrorCode.INVALID_ADDRESS_DETAIL);
        BigDecimal validLatitude = validateCoordinate(latitude, PlaceImportPolicy.LATITUDE_MIN,
                PlaceImportPolicy.LATITUDE_MAX, PlaceErrorCode.INVALID_LATITUDE);
        BigDecimal validLongitude = validateCoordinate(longitude, PlaceImportPolicy.LONGITUDE_MIN,
                PlaceImportPolicy.LONGITUDE_MAX, PlaceErrorCode.INVALID_LONGITUDE);
        if (regionCode == null) {
            throw new BusinessException(PlaceErrorCode.INVALID_REGION);
        }
        this.placeType = placeType;
        this.status = status;
        updateDisplay(validName, validRoadAddress, validAddressDetail, validLatitude, validLongitude, regionCode);
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
    }

    /** 유형이 정해지지 않은 장소는 노출할 수 없어 승인하지 못한다. 거절은 된다. */
    private static void validateType(PlaceType placeType, PlaceStatus status) {
        if (placeType == null) {
            throw new BusinessException(PlaceErrorCode.INVALID_PLACE_TYPE);
        }
        if (status == PlaceStatus.APPROVED && placeType == PlaceType.UNCLASSIFIED) {
            throw new BusinessException(PlaceErrorCode.UNCLASSIFIED_NOT_APPROVABLE);
        }
    }

    private static String validateText(String value, int minLength, int maxLength, PlaceErrorCode errorCode) {
        if (value == null) {
            throw new BusinessException(errorCode);
        }
        String stripped = value.strip();
        if (stripped.length() < minLength || stripped.length() > maxLength) {
            throw new BusinessException(errorCode);
        }
        return stripped;
    }

    private static BigDecimal validateCoordinate(BigDecimal value, BigDecimal min, BigDecimal max,
            PlaceErrorCode errorCode) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new BusinessException(errorCode);
        }
        return value.setScale(PlaceImportPolicy.COORDINATE_SCALE, RoundingMode.HALF_UP);
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
