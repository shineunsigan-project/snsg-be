package com.shineunsigan.snsg.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.place.application.dto.command.ListPlacesCommand;
import com.shineunsigan.snsg.place.application.dto.result.PlaceReviewPageResult;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.exception.PlaceErrorCode;
import com.shineunsigan.snsg.place.domain.model.Place;
import com.shineunsigan.snsg.place.domain.model.PlaceCursor;
import com.shineunsigan.snsg.place.domain.model.PlaceSearchCondition;
import com.shineunsigan.snsg.place.domain.repository.PlaceRepository;

@ExtendWith(MockitoExtension.class)
class PlaceReviewQueryServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 2, 10, 15, 30);

    @Mock
    private PlaceRepository placeRepository;

    private PlaceReviewQueryService placeReviewQueryService;

    @BeforeEach
    void setUp() {
        placeReviewQueryService = new PlaceReviewQueryService(placeRepository, new PlaceProperties(
                new BigDecimal("0.3"), Duration.ofSeconds(10), 3, List.of(), List.of(), Map.of(), 20, 50));
    }

    private static ListPlacesCommand command(String cursor, Integer size) {
        return new ListPlacesCommand(null, null, null, null, null, null, cursor, size);
    }

    private static Place place() {
        Place place = Place.createCandidate(PlaceSource.SEMAS, "TEST-" + UUID.randomUUID(), PlaceType.ESCAPE_ROOM,
                "원천이름", "서울특별시 마포구 원천로 1", "", new BigDecimal("37.5"), new BigDecimal("126.9"), "11440",
                CREATED_AT);
        ReflectionTestUtils.setField(place, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(place, "createdAt", CREATED_AT);
        return place;
    }

    private static List<Place> places(int count) {
        return IntStream.range(0, count).mapToObj(i -> place()).toList();
    }

    @ParameterizedTest(name = "size {0} → 한 페이지 {1}개")
    @CsvSource(value = {"NULL, 20", "0, 1", "-5, 1", "50, 50", "51, 50"}, nullValues = "NULL")
    @DisplayName("size는 생략하면 기본값, 상한을 넘으면 상한, 1보다 작으면 1로 잘라내고 한 건 더 읽는다")
    void pageSizeIsClamped(Integer size, int expected) {
        when(placeRepository.findAllForReview(any(), isNull(), anyInt())).thenReturn(List.of());

        placeReviewQueryService.list(command(null, size));

        verify(placeRepository).findAllForReview(any(), isNull(), eq(expected + 1));
    }

    @Test
    @DisplayName("한 건 더 읽혔으면 한 페이지만 주고 그 마지막 장소로 다음 커서를 만든다")
    void hasNextPage() {
        List<Place> read = places(3);
        when(placeRepository.findAllForReview(any(), isNull(), eq(3))).thenReturn(read);

        PlaceReviewPageResult result = placeReviewQueryService.list(command(null, 2));

        assertThat(result.items()).hasSize(2);
        assertThat(PlaceCursor.decode(result.nextCursor()))
                .isEqualTo(new PlaceCursor(CREATED_AT, read.get(1).getId()));
    }

    @Test
    @DisplayName("남은 개수가 size와 딱 맞으면 마지막 페이지라 nextCursor는 null이다")
    void lastPage() {
        when(placeRepository.findAllForReview(any(), isNull(), eq(3))).thenReturn(places(2));

        PlaceReviewPageResult result = placeReviewQueryService.list(command(null, 2));

        assertThat(result.items()).hasSize(2);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("커서는 해석해 그 다음부터 읽는다. 해석할 수 없으면 INVALID_CURSOR이고 조회하지 않는다")
    void cursor() {
        PlaceCursor cursor = new PlaceCursor(CREATED_AT, UUID.randomUUID());
        when(placeRepository.findAllForReview(any(), eq(cursor), anyInt())).thenReturn(List.of());

        placeReviewQueryService.list(command(cursor.encode(), null));

        assertThatThrownBy(() -> placeReviewQueryService.list(command("not a cursor!", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_CURSOR);
        verify(placeRepository).findAllForReview(any(), eq(cursor), anyInt());
    }

    @Test
    @DisplayName("비었거나 공백뿐인 지역·키워드는 조건으로 걸지 않고, 키워드는 앞뒤 공백을 뺀다")
    void blankFiltersAreIgnored() {
        when(placeRepository.findAllForReview(any(), isNull(), anyInt())).thenReturn(List.of());

        placeReviewQueryService.list(new ListPlacesCommand(" ", PlaceStatus.CANDIDATE, null, null, true, "  홍대 ",
                null, null));
        placeReviewQueryService.list(new ListPlacesCommand("", null, null, null, null, "   ", null, null));

        ArgumentCaptor<PlaceSearchCondition> conditions = ArgumentCaptor.forClass(PlaceSearchCondition.class);
        verify(placeRepository, times(2))
                .findAllForReview(conditions.capture(), isNull(), anyInt());
        assertThat(conditions.getAllValues()).containsExactly(
                new PlaceSearchCondition(null, PlaceStatus.CANDIDATE, null, null, true, "홍대"),
                new PlaceSearchCondition(null, null, null, null, null, null));
    }

    @Test
    @DisplayName("없는 장소의 상세는 PLACE_NOT_FOUND")
    void getNotFound() {
        UUID placeId = UUID.randomUUID();
        when(placeRepository.findById(placeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeReviewQueryService.get(placeId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("MANUAL 장소는 CHAR(5)로 읽힌 원천 지역의 공백을 빼고, 소수 7자리로 읽힌 원천 좌표 0을 0으로 준다")
    void manualSourceValuesAreNormalized() {
        UUID placeId = UUID.randomUUID();
        Place manual = Place.createManual(PlaceType.ESCAPE_ROOM, "장소", "주소", "", new BigDecimal("37.5"),
                new BigDecimal("126.9"), "11440", "admin", CREATED_AT);
        ReflectionTestUtils.setField(manual, "sourceRegionCode", "     ");
        ReflectionTestUtils.setField(manual, "sourceLatitude", new BigDecimal("0.0000000"));
        ReflectionTestUtils.setField(manual, "sourceLongitude", new BigDecimal("0.0000000"));
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(manual));

        assertThat(placeReviewQueryService.get(placeId))
                .extracting(result -> result.sourceRegionCode(), result -> result.sourceLatitude().toString(),
                        result -> result.sourceLongitude().toString())
                .containsExactly("", "0", "0");
    }
}
