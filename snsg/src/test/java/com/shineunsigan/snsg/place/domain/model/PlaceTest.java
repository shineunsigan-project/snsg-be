package com.shineunsigan.snsg.place.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.exception.PlaceErrorCode;

/**
 * 원천 확인을 반영할 때 검수 상태별로 무엇을 바꾸고 무엇을 지키는지, 폐업이 무엇을 바꾸는지 본다.
 * 원천 확인 테스트는 APPROVED·REJECTED와 검수에서 고친 화면 값을 필드를 직접 바꿔 만든다.
 * 어드민 수정·MANUAL 등록의 규칙(상태 거부, 유형 미정 승인 거부, 원천 값 보존, 초기값, 형식)은 중첩 클래스가 본다.
 */
class PlaceTest {

    private static final LocalDateTime FIRST_SYNC = LocalDateTime.of(2026, 10, 27, 0, 0);
    private static final LocalDateTime NEXT_SYNC = LocalDateTime.of(2027, 1, 5, 3, 0);
    private static final BigDecimal LAT = new BigDecimal("37.5775410");
    private static final BigDecimal LNG = new BigDecimal("126.9103629");
    private static final BigDecimal NEW_LAT = new BigDecimal("37.5000000");
    private static final BigDecimal NEW_LNG = new BigDecimal("126.9000000");

    private Place candidate() {
        return Place.createCandidate(PlaceSource.SEMAS, "TEST-1", PlaceType.ESCAPE_ROOM,
                "원천이름", "서울특별시 마포구 원천로 1", "2층", LAT, LNG, "11440", FIRST_SYNC);
    }

    /** 검수자가 상태를 정하고 화면 값을 고친 행. */
    private Place reviewed(PlaceStatus status) {
        Place place = candidate();
        ReflectionTestUtils.setField(place, "status", status);
        ReflectionTestUtils.setField(place, "name", "고친이름");
        ReflectionTestUtils.setField(place, "roadAddress", "서울특별시 마포구 고친로 1");
        ReflectionTestUtils.setField(place, "addressDetail", "3층");
        return place;
    }

    private boolean confirmWithNewSource(Place place) {
        return place.confirmSource("새이름", "서울특별시 서대문구 새로 2", "지하 1층",
                NEW_LAT, NEW_LNG, "11410", NEXT_SYNC);
    }

    private boolean confirmWithSameSource(Place place) {
        return place.confirmSource("원천이름", "서울특별시 마포구 원천로 1", "2층", LAT, LNG, "11440", NEXT_SYNC);
    }

    @Test
    @DisplayName("새 후보는 검수 대기·영업 중이고 화면 값과 원천 값이 같은 원천 값이다")
    void createCandidate() {
        Place place = candidate();

        assertThat(place.getStatus()).isEqualTo(PlaceStatus.CANDIDATE);
        assertThat(place.isClosed()).isFalse();
        assertThat(place.getPlaceType()).isEqualTo(PlaceType.ESCAPE_ROOM);
        assertThat(place.getLastSyncedAt()).isEqualTo(FIRST_SYNC);
        assertThat(place).extracting("name", "roadAddress", "addressDetail", "latitude", "longitude", "regionCode")
                .containsExactly("원천이름", "서울특별시 마포구 원천로 1", "2층", LAT, LNG, "11440");
        assertThat(place).extracting("sourceName", "sourceRoadAddress", "sourceAddressDetail",
                        "sourceLatitude", "sourceLongitude", "sourceRegionCode")
                .containsExactly("원천이름", "서울특별시 마포구 원천로 1", "2층", LAT, LNG, "11440");
    }

    @Test
    @DisplayName("검수 대기 후보를 다시 확인하면 화면 값과 원천 값이 모두 새 원천 값이 되고 유형·상태는 그대로다")
    void confirmCandidateUpdatesDisplayAndSource() {
        Place place = candidate();
        place.close();

        boolean needsReview = confirmWithNewSource(place);

        assertThat(needsReview).isFalse();
        assertThat(place).extracting("name", "roadAddress", "addressDetail", "latitude", "longitude", "regionCode")
                .containsExactly("새이름", "서울특별시 서대문구 새로 2", "지하 1층", NEW_LAT, NEW_LNG, "11410");
        assertThat(place).extracting("sourceName", "sourceRoadAddress", "sourceAddressDetail",
                        "sourceLatitude", "sourceLongitude", "sourceRegionCode")
                .containsExactly("새이름", "서울특별시 서대문구 새로 2", "지하 1층", NEW_LAT, NEW_LNG, "11410");
        assertThat(place.getPlaceType()).isEqualTo(PlaceType.ESCAPE_ROOM);
        assertThat(place.getStatus()).isEqualTo(PlaceStatus.CANDIDATE);
        assertThat(place.isClosed()).isFalse();
        assertThat(place.getLastSyncedAt()).isEqualTo(NEXT_SYNC);
    }

    @Test
    @DisplayName("승인된 장소를 다시 확인하면 원천 값만 바뀌고 검수에서 고친 화면 값은 그대로다")
    void confirmApprovedKeepsDisplay() {
        Place place = reviewed(PlaceStatus.APPROVED);

        confirmWithNewSource(place);

        assertThatDisplayIsReviewed(place);
        assertThatSourceIsNew(place);
        assertThat(place.getStatus()).isEqualTo(PlaceStatus.APPROVED);
        assertThat(place.getPlaceType()).isEqualTo(PlaceType.ESCAPE_ROOM);
        assertThat(place.isClosed()).isFalse();
        assertThat(place.getLastSyncedAt()).isEqualTo(NEXT_SYNC);
    }

    @Test
    @DisplayName("거절된 장소를 다시 확인하면 원천 값만 바뀌고 화면 값은 그대로다")
    void confirmRejectedKeepsDisplay() {
        Place place = reviewed(PlaceStatus.REJECTED);

        confirmWithNewSource(place);

        assertThatDisplayIsReviewed(place);
        assertThatSourceIsNew(place);
        assertThat(place.getStatus()).isEqualTo(PlaceStatus.REJECTED);
        assertThat(place.isClosed()).isFalse();
        assertThat(place.getLastSyncedAt()).isEqualTo(NEXT_SYNC);
    }

    @Test
    @DisplayName("승인된 장소의 원천 값이 지난 원천 값과 다르면 검수가 필요하다고 알린다")
    void approvedSourceChangedNeedsReview() {
        assertThat(confirmWithNewSource(reviewed(PlaceStatus.APPROVED))).isTrue();
    }

    @Test
    @DisplayName("승인된 장소의 상세주소만 바뀌어도 검수가 필요하다고 알린다")
    void approvedAddressDetailChangedNeedsReview() {
        Place place = reviewed(PlaceStatus.APPROVED);

        boolean needsReview = place.confirmSource("원천이름", "서울특별시 마포구 원천로 1", "3층",
                LAT, LNG, "11440", NEXT_SYNC);

        assertThat(needsReview).isTrue();
    }

    @Test
    @DisplayName("승인된 장소의 원천 값이 그대로면 화면 값과 달라도 검수가 필요하지 않다")
    void approvedSameSourceNeedsNoReview() {
        assertThat(confirmWithSameSource(reviewed(PlaceStatus.APPROVED))).isFalse();
    }

    @Test
    @DisplayName("좌표의 소수 자릿수 표기만 다르면 같은 원천 값으로 본다")
    void coordinatesComparedByValue() {
        Place place = reviewed(PlaceStatus.APPROVED);

        boolean needsReview = place.confirmSource("원천이름", "서울특별시 마포구 원천로 1", "2층",
                new BigDecimal("37.577541"), new BigDecimal("126.91036290"), "11440", NEXT_SYNC);

        assertThat(needsReview).isFalse();
    }

    @Test
    @DisplayName("거절된 장소는 원천 값이 바뀌어도 검수가 필요하다고 알리지 않는다")
    void rejectedSourceChangedNeedsNoReview() {
        assertThat(confirmWithNewSource(reviewed(PlaceStatus.REJECTED))).isFalse();
    }

    @Test
    @DisplayName("폐업이던 승인 장소가 다시 확인되면 폐업이 풀리고 고친 화면 값은 그대로다")
    void reconfirmClosedApprovedReopens() {
        Place place = reviewed(PlaceStatus.APPROVED);
        place.close();

        confirmWithSameSource(place);

        assertThat(place.isClosed()).isFalse();
        assertThat(place.getStatus()).isEqualTo(PlaceStatus.APPROVED);
        assertThatDisplayIsReviewed(place);
    }

    @Test
    @DisplayName("폐업 처리는 폐업 여부만 바꾸고 검수 상태와 화면 값·원천 값은 그대로 둔다")
    void closeKeepsStatusAndValues() {
        Place place = reviewed(PlaceStatus.APPROVED);

        place.close();

        assertThat(place.isClosed()).isTrue();
        assertThat(place.getStatus()).isEqualTo(PlaceStatus.APPROVED);
        assertThatDisplayIsReviewed(place);
        assertThat(place.getSourceName()).isEqualTo("원천이름");
    }

    @Test
    @DisplayName("검수 대기 후보를 폐업 처리하면 폐업 여부만 바뀌고 검수 대기 상태와 값은 그대로다")
    void closeCandidateKeepsStatusAndValues() {
        Place place = candidate();

        place.close();

        assertThat(place.isClosed()).isTrue();
        assertThat(place).extracting("status", "name", "roadAddress", "addressDetail", "sourceName")
                .containsExactly(PlaceStatus.CANDIDATE, "원천이름", "서울특별시 마포구 원천로 1", "2층", "원천이름");
    }

    private void assertThatDisplayIsReviewed(Place place) {
        assertThat(place).extracting("name", "roadAddress", "addressDetail", "latitude", "longitude", "regionCode")
                .containsExactly("고친이름", "서울특별시 마포구 고친로 1", "3층", LAT, LNG, "11440");
    }

    private void assertThatSourceIsNew(Place place) {
        assertThat(place).extracting("sourceName", "sourceRoadAddress", "sourceAddressDetail",
                        "sourceLatitude", "sourceLongitude", "sourceRegionCode")
                .containsExactly("새이름", "서울특별시 서대문구 새로 2", "지하 1층", NEW_LAT, NEW_LNG, "11410");
    }

    private static ErrorCode errorCodeOf(Throwable throwable) {
        return ((BusinessException) throwable).getErrorCode();
    }

    @Nested
    @DisplayName("어드민 수정(검수)")
    class Review {

        private static final String ADMIN = "0192f3a4-5b6c-7d8e-9f01-23456789abcd";
        private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 11, 13, 14, 20);

        private void review(Place place, PlaceType placeType, PlaceStatus status) {
            place.review(placeType, status, "  검수이름 ", "서울특별시 마포구 검수로 3", "  4층  ",
                    new BigDecimal("37.55"), new BigDecimal("126.92"), "11410", ADMIN, REVIEWED_AT);
        }

        @Test
        @DisplayName("유형·검수 상태·화면 값이 바뀌고 이름·주소는 앞뒤 공백을 뺀다. 검수자와 검수 시각이 남는다")
        void reviewChangesDisplayAndRecordsReviewer() {
            Place place = candidate();

            review(place, PlaceType.BOARD_GAME_CAFE, PlaceStatus.APPROVED);

            assertThat(place).extracting("placeType", "status", "name", "roadAddress", "addressDetail", "regionCode",
                            "reviewedBy", "reviewedAt")
                    .containsExactly(PlaceType.BOARD_GAME_CAFE, PlaceStatus.APPROVED, "검수이름",
                            "서울특별시 마포구 검수로 3", "4층", "11410", ADMIN, REVIEWED_AT);
        }

        @Test
        @DisplayName("원천 값·폐업 여부·마지막 확인 시각은 바뀌지 않는다. 폐업한 장소도 고칠 수 있다")
        void reviewKeepsSourceClosedAndSyncedAt() {
            Place place = candidate();
            place.close();

            review(place, PlaceType.ESCAPE_ROOM, PlaceStatus.REJECTED);

            assertThat(place.getStatus()).isEqualTo(PlaceStatus.REJECTED);
            assertThat(place.isClosed()).isTrue();
            assertThat(place.getLastSyncedAt()).isEqualTo(FIRST_SYNC);
            assertThat(place).extracting("source", "sourceId", "sourceName", "sourceRoadAddress",
                            "sourceAddressDetail", "sourceLatitude", "sourceLongitude", "sourceRegionCode")
                    .containsExactly(PlaceSource.SEMAS, "TEST-1", "원천이름", "서울특별시 마포구 원천로 1", "2층",
                            LAT, LNG, "11440");
        }

        @ParameterizedTest
        @EnumSource(value = PlaceStatus.class, names = "CANDIDATE")
        @NullSource
        @DisplayName("검수 상태가 APPROVED·REJECTED가 아니면 INVALID_REVIEW_STATUS이고 아무것도 바뀌지 않는다")
        void rejectsOtherStatus(PlaceStatus status) {
            Place place = candidate();

            assertThatThrownBy(() -> review(place, PlaceType.ESCAPE_ROOM, status))
                    .extracting(PlaceTest::errorCodeOf)
                    .isEqualTo(PlaceErrorCode.INVALID_REVIEW_STATUS);
            assertThat(place).extracting("status", "name", "reviewedBy", "reviewedAt")
                    .containsExactly(PlaceStatus.CANDIDATE, "원천이름", "", null);
        }

        @Test
        @DisplayName("유형 미정으로 승인하면 UNCLASSIFIED_NOT_APPROVABLE이고 아무것도 바뀌지 않는다")
        void unclassifiedCannotBeApproved() {
            Place place = candidate();

            assertThatThrownBy(() -> review(place, PlaceType.UNCLASSIFIED, PlaceStatus.APPROVED))
                    .extracting(PlaceTest::errorCodeOf)
                    .isEqualTo(PlaceErrorCode.UNCLASSIFIED_NOT_APPROVABLE);
            assertThat(place).extracting("placeType", "status", "name")
                    .containsExactly(PlaceType.ESCAPE_ROOM, PlaceStatus.CANDIDATE, "원천이름");
        }

        @Test
        @DisplayName("유형 미정이어도 거절은 된다")
        void unclassifiedCanBeRejected() {
            Place place = candidate();

            review(place, PlaceType.UNCLASSIFIED, PlaceStatus.REJECTED);

            assertThat(place).extracting("placeType", "status")
                    .containsExactly(PlaceType.UNCLASSIFIED, PlaceStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("MANUAL 등록")
    class CreateManual {

        private static final String ADMIN = "0192f3a4-5b6c-7d8e-9f01-23456789abcd";
        private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 14, 20);

        @Test
        @DisplayName("MANUAL·승인·영업 중이고, 원천 식별자와 원천 값은 ''·0, 마지막 확인 시각은 등록 시각이다")
        void initialValues() {
            Place place = Place.createManual(PlaceType.BOARD_GAME_CAFE, " 레드버튼 ", "서울특별시 서대문구 연세로 1", "",
                    new BigDecimal("37.5571234"), new BigDecimal("126.9361234"), "11410", ADMIN, NOW);

            assertThat(place).extracting("source", "status", "closed", "placeType", "name", "regionCode",
                            "lastSyncedAt", "reviewedBy", "reviewedAt")
                    .containsExactly(PlaceSource.MANUAL, PlaceStatus.APPROVED, false, PlaceType.BOARD_GAME_CAFE,
                            "레드버튼", "11410", NOW, ADMIN, NOW);
            assertThat(place).extracting("sourceId", "sourceName", "sourceRoadAddress", "sourceAddressDetail",
                            "sourceRegionCode")
                    .containsOnly("");
            assertThat(place.getSourceLatitude()).isZero();
            assertThat(place.getSourceLongitude()).isZero();
        }

        @Test
        @DisplayName("유형 미정은 등록할 수 없다(등록하면 바로 승인이다)")
        void unclassifiedCannotBeCreated() {
            assertThatThrownBy(() -> Place.createManual(PlaceType.UNCLASSIFIED, "레드버튼", "서울특별시 서대문구 연세로 1",
                    "", new BigDecimal("37.5"), new BigDecimal("126.9"), "11410", ADMIN, NOW))
                    .extracting(PlaceTest::errorCodeOf)
                    .isEqualTo(PlaceErrorCode.UNCLASSIFIED_NOT_APPROVABLE);
        }
    }

    @Nested
    @DisplayName("수정·등록의 형식 규칙")
    class Format {

        private Place create(String name, String roadAddress, String addressDetail, String latitude,
                String longitude) {
            return Place.createManual(PlaceType.ESCAPE_ROOM, name, roadAddress, addressDetail,
                    latitude == null ? null : new BigDecimal(latitude),
                    longitude == null ? null : new BigDecimal(longitude), "11440", "admin", FIRST_SYNC);
        }

        private Place createWithCoordinate(String latitude, String longitude) {
            return create("장소", "주소", "", latitude, longitude);
        }

        @ParameterizedTest(name = "위도 {0}, 경도 {1}")
        @CsvSource({"33, 124", "39, 132", "33.0000000, 132.0000000"})
        @DisplayName("좌표 범위는 경계를 포함한다")
        void coordinateBoundariesAreAllowed(String latitude, String longitude) {
            assertThat(createWithCoordinate(latitude, longitude).getStatus()).isEqualTo(PlaceStatus.APPROVED);
        }

        @Test
        @DisplayName("좌표는 소수 7자리로 HALF_UP 반올림한다")
        void coordinateIsRoundedHalfUp() {
            Place place = createWithCoordinate("37.12345675", "126.12345674");

            assertThat(place.getLatitude()).isEqualTo(new BigDecimal("37.1234568"));
            assertThat(place.getLongitude()).isEqualTo(new BigDecimal("126.1234567"));
        }

        @ParameterizedTest(name = "위도 {0}")
        @CsvSource(value = {"32.9999999", "39.0000001", "NULL"}, nullValues = "NULL")
        @DisplayName("위도가 비었거나 33~39 밖이면 INVALID_LATITUDE")
        void latitudeOutOfRange(String latitude) {
            assertThatThrownBy(() -> createWithCoordinate(latitude, "126.9"))
                    .extracting(PlaceTest::errorCodeOf)
                    .isEqualTo(PlaceErrorCode.INVALID_LATITUDE);
        }

        @ParameterizedTest(name = "경도 {0}")
        @CsvSource(value = {"123.9999999", "132.0000001", "NULL"}, nullValues = "NULL")
        @DisplayName("경도가 비었거나 124~132 밖이면 INVALID_LONGITUDE")
        void longitudeOutOfRange(String longitude) {
            assertThatThrownBy(() -> createWithCoordinate("37.5", longitude))
                    .extracting(PlaceTest::errorCodeOf)
                    .isEqualTo(PlaceErrorCode.INVALID_LONGITUDE);
        }

        @Test
        @DisplayName("앞뒤 공백을 뺀 길이로 장소명 100자·도로명 주소 200자·상세주소 100자까지 되고 상세주소는 비어도 된다")
        void textLengthBoundaries() {
            Place place = create(" " + "가".repeat(100) + " ", "나".repeat(200), "   ", "37.5", "126.9");

            assertThat(place.getName()).hasSize(100);
            assertThat(place.getRoadAddress()).hasSize(200);
            assertThat(place.getAddressDetail()).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(value = {
                "장소명 공백뿐, '   ', 주소, '', INVALID_PLACE_NAME",
                "장소명 101자, LONG_NAME, 주소, '', INVALID_PLACE_NAME",
                "장소명 null, NULL, 주소, '', INVALID_PLACE_NAME",
                "도로명 주소 빈 값, 장소, '', '', INVALID_ROAD_ADDRESS",
                "도로명 주소 201자, 장소, LONG_ADDRESS, '', INVALID_ROAD_ADDRESS",
                "상세주소 101자, 장소, 주소, LONG_NAME, INVALID_ADDRESS_DETAIL",
                "상세주소 null, 장소, 주소, NULL, INVALID_ADDRESS_DETAIL"}, nullValues = "NULL")
        @DisplayName("이름·주소 형식이 틀리면 필드별 오류다")
        void invalidText(String caseName, String name, String roadAddress, String addressDetail,
                PlaceErrorCode expected) {
            assertThatThrownBy(() -> create(expand(name), expand(roadAddress), expand(addressDetail), "37.5", "126.9"))
                    .extracting(PlaceTest::errorCodeOf)
                    .isEqualTo(expected);
        }

        private String expand(String value) {
            if ("LONG_NAME".equals(value)) {
                return "가".repeat(101);
            }
            if ("LONG_ADDRESS".equals(value)) {
                return "나".repeat(201);
            }
            return value;
        }

        @Test
        @DisplayName("유형이 없으면 INVALID_PLACE_TYPE")
        void placeTypeIsRequired() {
            assertThatThrownBy(() -> Place.createManual(null, "장소", "주소", "", new BigDecimal("37.5"),
                    new BigDecimal("126.9"), "11440", "admin", FIRST_SYNC))
                    .extracting(PlaceTest::errorCodeOf)
                    .isEqualTo(PlaceErrorCode.INVALID_PLACE_TYPE);
        }
    }
}
