package com.shineunsigan.snsg.place.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 원천 확인을 반영할 때 검수 상태별로 무엇을 바꾸고 무엇을 지키는지, 폐업이 무엇을 바꾸는지 본다.
 * 검수는 DB에서 직접 하므로 APPROVED·REJECTED와 검수에서 고친 화면 값은 필드를 직접 바꿔 만든다.
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
}
