package com.shineunsigan.snsg.place.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 상가정보 원천 행의 적재 대상 판정, 유형 추정, 값 정리를 본다. 설정은 application.yaml과 같은 값이다.
 */
class PlaceImportPolicyTest {

    private static final String TARGET_CATEGORY = "R10405";
    private static final String OTHER_CATEGORY = "I21201";
    private static final String LAT = "37.5775410";
    private static final String LON = "126.9103629";

    private final PlaceImportPolicy policy = new PlaceImportPolicy(
            List.of("R10405", "R10499"),
            List.of("보드게임", "보드카페", "방탈출"),
            Map.of(PlaceType.BOARD_GAME_CAFE, List.of("보드"),
                    PlaceType.ESCAPE_ROOM, List.of("방탈출", "이스케이프", "escape")));

    private static SourceStore store(String category, String storeName, String branchName) {
        return new SourceStore("MA0001", storeName, branchName, category, "서울특별시 마포구 와우산로 1",
                "서울특별시 마포구 서교동 1-1", "3", LON, LAT);
    }

    private static SourceStore coordinates(String longitude, String latitude) {
        return new SourceStore("MA0001", "더홀릭보드게임카페", "", TARGET_CATEGORY, "서울특별시 마포구 와우산로 1",
                "", "", longitude, latitude);
    }

    // 적재 대상

    @Test
    @DisplayName("지정 소분류면 상호에 키워드가 없어도 적재 대상이다")
    void targetCategoryWithoutKeyword() {
        assertThat(policy.isTarget(store(TARGET_CATEGORY, "즐거운오락실", ""))).isTrue();
    }

    @Test
    @DisplayName("다른 소분류라도 상호명에 적재 키워드가 들면 대상이다")
    void otherCategoryWithKeywordInName() {
        assertThat(policy.isTarget(store(OTHER_CATEGORY, "레드버튼보드게임카페", ""))).isTrue();
    }

    @Test
    @DisplayName("다른 소분류라도 지점명에만 적재 키워드가 들면 대상이다")
    void otherCategoryWithKeywordOnlyInBranch() {
        assertThat(policy.isTarget(store(OTHER_CATEGORY, "키이스케이프", "방탈출 홍대점"))).isTrue();
    }

    @Test
    @DisplayName("다른 소분류에 '보드'만 든 상호는 대상이 아니다")
    void otherCategoryWithOnlyBoard() {
        assertThat(policy.isTarget(store(OTHER_CATEGORY, "보드람치킨", "신촌점"))).isFalse();
        assertThat(policy.toImportable(store(OTHER_CATEGORY, "보드람치킨", "신촌점"))).isEmpty();
    }

    @Test
    @DisplayName("영문 적재 키워드는 대소문자만 다른 상호도 대상이다")
    void englishKeywordIgnoresCase() {
        PlaceImportPolicy englishKeyword = new PlaceImportPolicy(List.of(), List.of("Escape"), Map.of());

        assertThat(englishKeyword.isTarget(store(OTHER_CATEGORY, "SECRET ESCAPE", ""))).isTrue();
        assertThat(englishKeyword.isTarget(store(OTHER_CATEGORY, "secret escape", ""))).isTrue();
    }

    // 유형 추정

    @Test
    @DisplayName("보드게임 카페 키워드만 들면 보드게임 카페다")
    void boardGameCafeType() {
        assertThat(policy.estimateType(store(TARGET_CATEGORY, "홈즈앤루팡 보드카페", "")))
                .isEqualTo(PlaceType.BOARD_GAME_CAFE);
    }

    @Test
    @DisplayName("방탈출 키워드만 들면 방탈출이다. 지점명에 든 키워드도 본다")
    void escapeRoomType() {
        assertThat(policy.estimateType(store(TARGET_CATEGORY, "키이스케이프", "강남점"))).isEqualTo(PlaceType.ESCAPE_ROOM);
        assertThat(policy.estimateType(store(TARGET_CATEGORY, "제로월드", "방탈출 홍대점"))).isEqualTo(PlaceType.ESCAPE_ROOM);
    }

    @Test
    @DisplayName("영문 대문자 ESCAPE도 방탈출이다")
    void upperCaseEscape() {
        assertThat(policy.estimateType(store(TARGET_CATEGORY, "SECRET ESCAPE", ""))).isEqualTo(PlaceType.ESCAPE_ROOM);
    }

    @Test
    @DisplayName("유형 키워드가 없으면 미분류다")
    void noTypeKeyword() {
        assertThat(policy.estimateType(store(TARGET_CATEGORY, "즐거운오락실", ""))).isEqualTo(PlaceType.UNCLASSIFIED);
    }

    @Test
    @DisplayName("두 유형의 키워드가 함께 걸리면 미분류다")
    void bothTypeKeywords() {
        assertThat(policy.estimateType(store(TARGET_CATEGORY, "보드게임 방탈출", ""))).isEqualTo(PlaceType.UNCLASSIFIED);
    }

    // 값 정리: 이름

    @Test
    @DisplayName("이름은 앞뒤 공백을 뺀 상호명과 지점명을 공백 하나로 잇는다")
    void nameJoinsStoreAndBranch() {
        assertThat(PlaceImportPolicy.name(store(TARGET_CATEGORY, "  더홀릭보드게임카페 ", " 신촌점  ")))
                .isEqualTo("더홀릭보드게임카페 신촌점");
    }

    @Test
    @DisplayName("지점명이 비었거나 없으면 상호명만 쓴다")
    void nameWithoutBranch() {
        assertThat(PlaceImportPolicy.name(store(TARGET_CATEGORY, " 더홀릭보드게임카페 ", "  "))).isEqualTo("더홀릭보드게임카페");
        assertThat(PlaceImportPolicy.name(store(TARGET_CATEGORY, "더홀릭보드게임카페", null))).isEqualTo("더홀릭보드게임카페");
    }

    @Test
    @DisplayName("이름은 100자에서 자른다")
    void nameTruncatedAt100() {
        String name = PlaceImportPolicy.name(store(TARGET_CATEGORY, "가".repeat(95), "나".repeat(10)));

        assertThat(name).hasSize(100).isEqualTo("가".repeat(95) + " " + "나".repeat(4));
    }

    // 값 정리: 주소

    @Test
    @DisplayName("주소는 도로명 주소를 쓴다")
    void roadAddressFirst() {
        SourceStore store = new SourceStore("MA0001", "a", "", TARGET_CATEGORY, " 서울특별시 마포구 와우산로 1 ",
                "서울특별시 마포구 서교동 1-1", "", LON, LAT);

        assertThat(PlaceImportPolicy.address(store)).isEqualTo("서울특별시 마포구 와우산로 1");
    }

    @Test
    @DisplayName("도로명 주소가 비면 지번 주소를 쓴다")
    void lotAddressWhenRoadEmpty() {
        SourceStore store = new SourceStore("MA0001", "a", "", TARGET_CATEGORY, "  ",
                "서울특별시 마포구 서교동 1-1", "", LON, LAT);

        assertThat(PlaceImportPolicy.address(store)).isEqualTo("서울특별시 마포구 서교동 1-1");
    }

    @Test
    @DisplayName("주소는 200자에서 자른다")
    void addressTruncatedAt200() {
        SourceStore store = new SourceStore("MA0001", "a", "", TARGET_CATEGORY, "가".repeat(250), "", "", LON, LAT);

        assertThat(PlaceImportPolicy.address(store)).isEqualTo("가".repeat(200));
    }

    // 값 정리: 상세주소(층)

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "3|3층",
            "' 3 '|3층",
            "B1|지하 1층",
            "지|지하",
            "1F|''",
            "옥상|''",
            "-1|''",
    })
    @DisplayName("층은 숫자 → n층, Bn → 지하 n층, 지 → 지하, 그 밖은 ''")
    void addressDetail(String floor, String expected) {
        assertThat(PlaceImportPolicy.addressDetail(floor)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("층이 비었거나 없으면 ''")
    void emptyFloor(String floor) {
        assertThat(PlaceImportPolicy.addressDetail(floor)).isEmpty();
    }

    // 값 정리: 좌표

    @ParameterizedTest(name = "lon={0}, lat={1}")
    @CsvSource({"124,33", "132,39", "124.0000000,39.0000000"})
    @DisplayName("좌표 범위의 경계값은 적재한다")
    void coordinateBoundaryIsImported(String longitude, String latitude) {
        assertThat(policy.toImportable(coordinates(longitude, latitude))).isPresent();
    }

    @ParameterizedTest(name = "lon={0}, lat={1}")
    @CsvSource({"126.9,32.9999999", "126.9,39.0000001", "123.9999999,37.5", "132.0000001,37.5"})
    @DisplayName("좌표가 범위 밖이면 그 행은 적재하지 않는다")
    void coordinateOutOfRangeIsSkipped(String longitude, String latitude) {
        assertThat(policy.toImportable(coordinates(longitude, latitude))).isEmpty();
    }

    @ParameterizedTest(name = "lon=\"{0}\", lat=\"{1}\"")
    @CsvSource(nullValues = "null", value = {"'',37.5", "126.9,''", "null,37.5", "abc,37.5", "' ',' '"})
    @DisplayName("좌표가 비었거나 숫자가 아니면 그 행은 적재하지 않는다")
    void emptyCoordinateIsSkipped(String longitude, String latitude) {
        assertThat(policy.toImportable(coordinates(longitude, latitude))).isEmpty();
    }

    @Test
    @DisplayName("좌표는 소수 7자리로 반올림(HALF_UP)한다")
    void coordinateRoundedTo7() {
        ImportablePlace place = policy.toImportable(coordinates("126.910362850", "37.57754104999")).orElseThrow();

        assertThat(place.longitude()).isEqualTo(new BigDecimal("126.9103629"));
        assertThat(place.latitude()).isEqualTo(new BigDecimal("37.5775410"));
    }

    // 상가업소번호

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("상가업소번호가 비면 적재하지 않는다")
    void emptySourceIdIsSkipped(String sourceId) {
        SourceStore store = new SourceStore(sourceId, "더홀릭보드게임카페", "", TARGET_CATEGORY, "서울특별시 마포구 와우산로 1",
                "", "", LON, LAT);

        assertThat(policy.toImportable(store)).isEmpty();
    }

    @Test
    @DisplayName("적재 대상이면 정리한 값과 추정 유형을 함께 준다")
    void toImportable() {
        SourceStore store = new SourceStore(" MA0001 ", "더홀릭보드게임카페", "신촌점", OTHER_CATEGORY,
                "서울특별시 서대문구 연세로 1", "", "B1", "126.9", "37.5");

        assertThat(policy.toImportable(store)).get()
                .extracting(ImportablePlace::sourceId, ImportablePlace::name, ImportablePlace::roadAddress,
                        ImportablePlace::addressDetail, ImportablePlace::latitude, ImportablePlace::longitude,
                        ImportablePlace::placeType)
                .containsExactly("MA0001", "더홀릭보드게임카페 신촌점", "서울특별시 서대문구 연세로 1", "지하 1층",
                        new BigDecimal("37.5000000"), new BigDecimal("126.9000000"), PlaceType.BOARD_GAME_CAFE);
    }
}
